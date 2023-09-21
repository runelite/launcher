/*
 * Copyright (c) 2022, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Bootstrap;

@Slf4j
class ForkLauncher
{
	static boolean canForkLaunch()
	{
		var os = OS.getOs();

		if (os == OS.OSType.Linux)
		{
			var appimage = System.getenv("APPIMAGE");
			if (appimage != null)
			{
				return true;
			}
		}

		if (os == OS.OSType.Windows || os == OS.OSType.MacOS)
		{
			ProcessHandle current = ProcessHandle.current();
			var command = current.info().command();
			if (command.isEmpty())
			{
				return false;
			}

			Path path = Paths.get(command.get());
			var name = path.getFileName().toString();
			return name.equals(Launcher.LAUNCHER_EXECUTABLE_NAME_WIN)
				|| name.equals(Launcher.LAUNCHER_EXECUTABLE_NAME_OSX);
		}

		return false;
	}

	static void launch(
		Bootstrap bootstrap,
		List<File> classpath,
		Collection<String> clientArgs,
		Map<String, String> jvmProps,
		List<String> jvmArgs) throws IOException
	{
		ProcessHandle current = ProcessHandle.current();
		Path path;

		switch (OS.getOs())
		{
			case Windows:
				path = Paths.get(current.info().command().get());
				break;
			case MacOS:
				path = Paths.get(current.info().command().get());
				// on macOS packr changes the cwd to the resource directory prior to launching the JVM,
				// causing current.info().command() to return /Applications/RuneLite.app/Contents/Resources/./RuneLite
				// despite the executable really being at /Applications/RuneLite.app/Contents/MacOS/RuneLite
				path = path.normalize()
					.resolveSibling(Path.of("..", "MacOS", path.getFileName().toString()))
					.normalize();
				break;
			case Linux:
				// the executable is in the fuse-mounted filesystem of the appimage, which goes away after the launcher
				// exits. So relaunch the appimage instead.
				var appimage = System.getenv("APPIMAGE");
				path = Path.of(appimage);
				break;
			default:
				throw new IllegalStateException("invalid os");
		}

		var commands = new ArrayList<>();
		commands.add(path.toAbsolutePath().toString());
		commands.add("-c");
		// bootstrap vm args
		var clientJvmArgs = JvmLauncher.getJvmArguments(bootstrap);
		if (clientJvmArgs != null)
		{
			for (var arg : clientJvmArgs)
			{
				commands.add("-J");
				commands.add(arg);
			}
		}
		// launcher vm props
		for (var prop : jvmProps.entrySet())
		{
			commands.add("-J");
			commands.add("-D" + prop.getKey() + "=" + prop.getValue());
		}
		// launcher vm args
		for (var arg : jvmArgs)
		{
			commands.add("-J");
			commands.add(arg);
		}

		// program arguments
		commands.add("--");

		if (classpath.isEmpty())
		{
			// avoid looping
			throw new RuntimeException("cannot fork launch with an empty classpath");
		}

		commands.add("--classpath");
		var sb = new StringBuilder();
		for (var f : classpath)
		{
			if (sb.length() > 0)
			{
				sb.append(File.pathSeparatorChar);
			}

			sb.append(f.getName());
		}
		commands.add(sb.toString());

		commands.addAll(clientArgs);

		log.debug("Running process: {}", commands);

		var builder = new ProcessBuilder(commands.toArray(new String[0]));
		builder.start();
	}
}

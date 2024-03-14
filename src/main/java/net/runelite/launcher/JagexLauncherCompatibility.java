/*
 * Copyright (c) 2024, YvesW <https://github.com/YvesW>
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

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import static net.runelite.launcher.Launcher.isProcessElevated;
import static net.runelite.launcher.Launcher.nativesLoaded;
import static net.runelite.launcher.Launcher.regDeleteValue;

@Slf4j
class JagexLauncherCompatibility
{
	// this is set to RUNASADMIN
	private static final String COMPAT_KEY = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers";

	static boolean check()
	{
		if (!nativesLoaded)
		{
			log.debug("Launcher natives were not loaded. Skipping Jagex launcher compatibility check.");
			return false;
		}

		ProcessHandle current = ProcessHandle.current();
		ProcessHandle parent = current.parent().orElse(null);

		// The only problematic configuration is for us to be running as admin & the Jagex launcher to *not* be running as admin
		if (parent == null || !processIsJagexLauncher(parent) || !isProcessElevated(current.pid()) || isProcessElevated(parent.pid()))
		{
			return false;
		}

		log.error("RuneLite is running with elevated permissions, but the Jagex launcher is not. Privileged processes " +
			"can't have environment variables passed to them from unprivileged processes. This will cause you to be " +
			"unable to login. Either run RuneLite as a regular user, or run the Jagex launcher as an administrator.");

		// attempt to fix this by removing the compatibility settings
		String command = current.info().command().orElse(null);
		boolean regEdited = false;
		if (command != null)
		{
			regEdited |= regDeleteValue("HKLM", COMPAT_KEY, command); // all users
			regEdited |= regDeleteValue("HKCU", COMPAT_KEY, command); // current user

			if (regEdited)
			{
				log.info("Application compatibility settings have been unset for {}", command);
			}
		}

		showErrorDialog(regEdited);
		return true;
	}

	private static boolean processIsJagexLauncher(ProcessHandle process)
	{
		var info = process.info();
		if (info.command().isEmpty())
		{
			return false;
		}
		return "JagexLauncher.exe".equals(pathFilename(info.command().get()));
	}

	private static String pathFilename(String path)
	{
		Path p = Paths.get(path);
		return p.getFileName().toString();
	}

	private static void showErrorDialog(boolean patched)
	{
		String command = ProcessHandle.current().info().command()
			.map(JagexLauncherCompatibility::pathFilename)
			.orElse(Launcher.LAUNCHER_EXECUTABLE_NAME_WIN);
		var sb = new StringBuilder();
		sb.append("Running RuneLite as an administrator is incompatible with the Jagex launcher.");
		if (patched)
		{
			sb.append(" RuneLite has attempted to fix this problem by changing the compatibility settings of ").append(command).append('.');
			sb.append(" Try running RuneLite again.");
		}
		sb.append(" If the problem persists, either run the Jagex launcher as administrator, or change the ")
			.append(command).append(" compatibility settings to not run as administrator.");

		final var message = sb.toString();
		SwingUtilities.invokeLater(() ->
			new FatalErrorDialog(message)
				.open());
	}
}

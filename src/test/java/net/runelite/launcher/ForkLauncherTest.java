/*
 * Copyright (c) 2026, Nick <nickspaghettidev@gmail.com>
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
import java.nio.file.Path;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ForkLauncherTest
{
	@Test
	public void testRelaunchCommand()
	{
		Path executable = Path.of("/Applications/RuneLite.app/Contents/MacOS/RuneLite");

		List<String> command = ForkLauncher.relaunchCommand(
			executable,
			List.of("-Xmx768m", "-Dapple.awt.application.appearance=system"),
			"client-1.11.jar" + File.pathSeparatorChar + "runelite-api-1.11.jar",
			List.of("--debug"));

		assertEquals(List.of(
			executable.toAbsolutePath().toString(),
			"-c",
			"-J", "-Xmx768m",
			"-J", "-Dapple.awt.application.appearance=system",
			"--",
			"--classpath", "client-1.11.jar" + File.pathSeparatorChar + "runelite-api-1.11.jar",
			"--debug"
		), command);
	}

	@Test
	public void testRelaunchCommandNoVmArgsOrClientArgs()
	{
		Path executable = Path.of("/Applications/RuneLite.app/Contents/MacOS/RuneLite");

		List<String> command = ForkLauncher.relaunchCommand(executable, List.of(), "client.jar", List.of());

		assertEquals(List.of(
			executable.toAbsolutePath().toString(),
			"-c",
			"--",
			"--classpath", "client.jar"
		), command);
	}

	@Test
	public void testClasspathOption()
	{
		// launch() sends jar names, not paths, as they are resolved against REPO_DIR by the child
		assertEquals("a.jar" + File.pathSeparatorChar + "b.jar",
			ForkLauncher.classpathOption(List.of(new File("/repo", "a.jar"), new File("/repo", "b.jar"))));
		assertEquals("", ForkLauncher.classpathOption(List.of()));
	}

	@Test
	public void testDockMenuIsNotInstalledWithoutACommand()
	{
		// must not throw, regardless of platform or headlessness
		DockMenu.install(null);
		DockMenu.install(List.of());
		assertTrue(true);
	}
}

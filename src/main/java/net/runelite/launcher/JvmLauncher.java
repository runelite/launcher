/*
* Copyright (c) 2018, Adam <Adam@sigterm.info>
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import static net.runelite.launcher.Launcher.CLIENT_MAIN_CLASS;
import net.runelite.launcher.beans.Bootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
class JvmLauncher
{
	private static final Logger logger = LoggerFactory.getLogger(JvmLauncher.class);

	private static String getJava() throws FileNotFoundException
	{
		Path javaHome = Paths.get(System.getProperty("java.home"));

		if (!Files.exists(javaHome))
		{
			throw new FileNotFoundException("JAVA_HOME is not set correctly! directory \"" + javaHome + "\" does not exist.");
		}

		Path javaPath = Paths.get(javaHome.toString(), "bin", "java.exe");

		if (!Files.exists(javaPath))
		{
			javaPath = Paths.get(javaHome.toString(), "bin", "java");
		}

		if (!Files.exists(javaPath))
		{
			throw new FileNotFoundException("java executable not found in directory \"" + javaPath.getParent() + "\"");
		}

		return javaPath.toAbsolutePath().toString();
	}

	static void launch(
		Bootstrap bootstrap,
		List<File> results,
		Collection<String> clientArgs,
		List<String> extraJvmParams) throws IOException
	{
		StringBuilder classPath = new StringBuilder();
		for (File f : results)
		{
			if (classPath.length() > 0)
			{
				classPath.append(File.pathSeparatorChar);
			}

			classPath.append(f.getAbsolutePath());
		}
		String javaExePath;
		try
		{
			javaExePath = getJava();
		}
		catch (FileNotFoundException ex)
		{
			logger.error("Unable to find java executable", ex);
			return;
		}


		List<String> arguments = new ArrayList<>();
		arguments.add(javaExePath);
		arguments.add("-cp");
		arguments.add(classPath.toString());

		String[] jvmArguments;
		String jvmVersion = System.getProperty("java.version");
		if (jvmVersion.startsWith("1."))
		{
			logger.info("Using Java version 1.x");
			jvmArguments = bootstrap.getClientJvmArguments();
		}
		else
		{
			logger.info("Using Java version 9+");
			jvmArguments = bootstrap.getClientJvm9Arguments();
		}
		arguments.addAll(Arrays.asList(jvmArguments));
		arguments.addAll(extraJvmParams);

		arguments.add(CLIENT_MAIN_CLASS);
		arguments.addAll(clientArgs);

		logger.info("Running {}", arguments);

		ProcessBuilder builder = new ProcessBuilder(arguments.toArray(new String[0]));
		builder.redirectErrorStream(true);
		Process process = builder.start();

		OpenOSRSSplashScreen.close();

		if (log.isDebugEnabled())
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			for (String line; (line = reader.readLine()) != null; )
			{
				System.out.println(line);
			}
		}
	}
}
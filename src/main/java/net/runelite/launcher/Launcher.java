/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
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

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.launcher.beans.Bootstrap;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher
{
	private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

	public static boolean DEBUG = false;

	private static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	private static final File REPO_DIR = new File(RUNELITE_DIR, "repository");

	private static final String CLIENT_BOOTSTRAP_URL = "http://static.runelite.net/bootstrap.json";
	private static final String CLIENT_MAIN_CLASS = "net.runelite.client.RuneLite";

	private static String getJava()
	{
		String path = System.getProperty("java.library.path");
		int i = path.indexOf(File.pathSeparator);
		if (i == -1)
		{
			return path;
		}

		path = path.substring(0, i);

		File java = new File(path, "java.exe");
		if (java.exists())
		{
			return java.getAbsolutePath();
		}
		else
		{
			return new File(path, "java").getAbsolutePath();
		}
	}

	public static void main(String[] args) throws Exception
	{
		Bootstrap bootstrap = getBootstrap();

		ArtifactResolver resolver = new ArtifactResolver(REPO_DIR);
		resolver.addRepositories();

		Artifact a = bootstrap.getClient();

		List<ArtifactResult> results = resolver.resolveArtifacts(a);
		StringBuilder classPath = new StringBuilder();

		for (ArtifactResult ar : results)
		{
			File f = ar.getArtifact().getFile();

			if (classPath.length() > 0)
			{
				classPath.append(File.pathSeparatorChar);
			}

			classPath.append(f.getAbsolutePath());
		}

		List<String> arguments = new ArrayList<>();
		arguments.add(getJava());
		arguments.add("-cp");
		arguments.add(classPath.toString());
		arguments.addAll(Arrays.asList(bootstrap.getClientJvmArguments()));
		arguments.add(CLIENT_MAIN_CLASS);

		logger.info("Running {}", arguments);

		ProcessBuilder builder = new ProcessBuilder(arguments.toArray(new String[0]));
		builder.redirectErrorStream(true);
		Process process = builder.start();

		if (DEBUG)
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			for (String line; (line = reader.readLine()) != null;)
			{
				System.out.println(line);
			}
		}
	}

	private static Bootstrap getBootstrap() throws Exception
	{
		URL u = new URL(CLIENT_BOOTSTRAP_URL);
		URLConnection conn = u.openConnection();
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		try (InputStream i = conn.getInputStream())
		{
			Gson g = new Gson();
			return g.fromJson(new InputStreamReader(i), Bootstrap.class);
		}
	}
}

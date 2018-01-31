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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.runelite.launcher.beans.Bootstrap;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher
{
	private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

	private static boolean verify = true;

	private static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	private static final File REPO_DIR = new File(RUNELITE_DIR, "repository");

	private static final String CLIENT_BOOTSTRAP_URL = "http://static.runelite.net/bootstrap.json";
	private static final String CLIENT_MAIN_CLASS = "net.runelite.client.RuneLite";

	private static OptionSet options;

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

	public static void main(String[] args) throws Exception
	{
		OptionParser parser = new OptionParser();
		parser.accepts("version").withRequiredArg();
		parser.accepts("clientargs").withRequiredArg();
		parser.accepts("debug");
		options = parser.parse(args);

		LauncherFrame frame = new LauncherFrame();

		Bootstrap bootstrap = getBootstrap();

		if (options.has("version"))
		{
			String version = (String) options.valueOf("version");
			logger.info("Using version {}", version);
			DefaultArtifact artifact = bootstrap.getClient();
			artifact = (DefaultArtifact) artifact.setVersion(version);
			bootstrap.setClient(artifact);

			verify = false; // non-releases are not signed
		}

		ArtifactResolver resolver = new ArtifactResolver(REPO_DIR);
		resolver.setListener(frame);
		resolver.addRepositories();

		Artifact a = bootstrap.getClient();

		List<ArtifactResult> results = resolver.resolveArtifacts(a);

		if (results.isEmpty())
		{
			logger.error("Unable to resolve artifacts");
			return;
		}

		try
		{
			verifyJarSignature(results.get(0).getArtifact().getFile());

			logger.info("Verified signature of {}", results.get(0).getArtifact());
		}
		catch (CertificateException | IOException | SecurityException ex)
		{
			if (verify)
			{
				logger.error("Unable to verify signature of jar file", ex);
				return;
			}
			else
			{
				logger.warn("Unable to verify signature of jar file", ex);
			}
		}

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

		String clientArgs = System.getenv("RUNELITE_ARGS");
		if (options.has("clientargs"))
		{
			clientArgs = (String) options.valueOf("clientargs");
		}

		List<String> arguments = new ArrayList<>();
		arguments.add(javaExePath);
		arguments.add("-cp");
		arguments.add(classPath.toString());
		arguments.addAll(Arrays.asList(bootstrap.getClientJvmArguments()));
		arguments.add(CLIENT_MAIN_CLASS);
		if (clientArgs != null)
		{
			arguments.add(clientArgs);
		}

		logger.info("Running {}", arguments);

		ProcessBuilder builder = new ProcessBuilder(arguments.toArray(new String[0]));
		builder.redirectErrorStream(true);
		Process process = builder.start();

		if (options.has("debug"))
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			for (String line; (line = reader.readLine()) != null;)
			{
				System.out.println(line);
			}
		}

		frame.setVisible(false);
		frame.dispose();
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

	private static void verifyJarSignature(File jarFile) throws CertificateException, IOException
	{
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		Certificate certificate = certFactory.generateCertificate(JarVerifier.class.getResourceAsStream("/runelite.crt"));

		JarVerifier.verify(new JarFile(jarFile), certificate);
	}
}

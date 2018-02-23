/*
 * Copyright (c) 2016-2018, Adam <Adam@sigterm.info>
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.jar.JarFile;
import javax.swing.UIManager;
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
	static final String CLIENT_MAIN_CLASS = "net.runelite.client.RuneLite";

	public static void main(String[] args) throws Exception
	{
		OptionParser parser = new OptionParser();
		parser.accepts("version").withRequiredArg();
		parser.accepts("clientargs").withRequiredArg();
		parser.accepts("nojvm");
		parser.accepts("debug");
		OptionSet options = parser.parse(args);

		try
		{
			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		}
		catch (Exception ex)
		{
			logger.warn("Unable to set cross platform look and feel", ex);
		}

		LauncherFrame frame = new LauncherFrame();

		Bootstrap bootstrap = getBootstrap();

		// update packr vmargs
		PackrConfig.updateLauncherArgs(bootstrap);

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

		frame.setVisible(false);
		frame.dispose();

		String clientArgs = getArgs(options);

		// packr doesn't let us specify command line arguments
		if ("true".equals(System.getProperty("runelite.launcher.nojvm")) || options.has("nojvm"))
		{
			ReflectionLauncher.launch(results, clientArgs, options);
		}
		else
		{
			JvmLauncher.launch(bootstrap, results, clientArgs, options);
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

	private static String getArgs(OptionSet options)
	{
		String clientArgs = System.getenv("RUNELITE_ARGS");
		if (options.has("clientargs"))
		{
			clientArgs = (String) options.valueOf("clientargs");
		}
		return clientArgs;
	}

	private static void verifyJarSignature(File jarFile) throws CertificateException, IOException
	{
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		Certificate certificate = certFactory.generateCertificate(JarVerifier.class.getResourceAsStream("/runelite.crt"));

		JarVerifier.verify(new JarFile(jarFile), certificate);
	}
}

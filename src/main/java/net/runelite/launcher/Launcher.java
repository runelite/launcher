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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import javax.swing.UIManager;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Artifact;
import net.runelite.launcher.beans.Bootstrap;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Slf4j
public class Launcher
{
	private static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	private static final File LOGS_DIR = new File(RUNELITE_DIR, "logs");
	private static final File LOGS_FILE_NAME = new File(LOGS_DIR, "launcher");
	private static final File REPO_DIR = new File(RUNELITE_DIR, "repository2");
	private static final String CLIENT_BOOTSTRAP_URL = "http://static.runelite.net/bootstrap.json";
	private static final String CLIENT_BOOTSTRAP_SHA256_URL = "http://static.runelite.net/bootstrap.json.sha256";
	private static final LauncherProperties PROPERTIES = new LauncherProperties();

	static final String CLIENT_MAIN_CLASS = "net.runelite.client.RuneLite";

	public static void main(String[] args)
	{
		OptionParser parser = new OptionParser();
		parser.accepts("clientargs").withRequiredArg();
		parser.accepts("nojvm");
		parser.accepts("debug");

		HardwareAccelerationMode defaultMode;
		switch (OS.getOs())
		{
			case Windows:
				defaultMode = HardwareAccelerationMode.DIRECTDRAW;
				break;
			case MacOS:
			case Linux:
				defaultMode = HardwareAccelerationMode.OPENGL;
				break;
			default:
				defaultMode = HardwareAccelerationMode.OFF;
				break;
		}

		// Create typed argument for the hardware acceleration mode
		final ArgumentAcceptingOptionSpec<HardwareAccelerationMode> mode = parser.accepts("mode")
			.withRequiredArg()
			.ofType(HardwareAccelerationMode.class)
			.defaultsTo(defaultMode);

		OptionSet options = parser.parse(args);

		// Always use IPv4 over IPv6
		System.setProperty("java.net.preferIPv4Stack", "true");

		// Setup debug
		final boolean isDebug = options.has("debug");
		LOGS_DIR.mkdirs();
		MDC.put("logFileName", LOGS_FILE_NAME.getAbsolutePath());

		if (isDebug)
		{
			final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
			logger.setLevel(Level.DEBUG);
		}

		// Get hardware acceleration mode
		final HardwareAccelerationMode hardwareAccelerationMode = options.valueOf(mode);

		// Setup hardware acceleration
		log.info("Setting hardware acceleration to {}", hardwareAccelerationMode);
		hardwareAccelerationMode.enable();

		// Setup launcher version
		final String launcherVersion = PROPERTIES.getVersion();
		System.setProperty(PROPERTIES.getVersionKey(), launcherVersion);

		// Setup extra JVM arguments
		final List<String> extraJvmParams = hardwareAccelerationMode.toParams();
		extraJvmParams.add("-D" + PROPERTIES.getVersionKey() + "=" + launcherVersion);

		try
		{
			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		}
		catch (Exception ex)
		{
			log.warn("Unable to set cross platform look and feel", ex);
		}

		LauncherFrame frame = new LauncherFrame();

		Bootstrap bootstrap;
		try
		{
			bootstrap = getBootstrap();
		}
		catch (IOException | VerificationException | CertificateException | SignatureException | InvalidKeyException | NoSuchAlgorithmException ex)
		{
			log.error("error fetching bootstrap", ex);
			frame.setVisible(false);
			frame.dispose();
			System.exit(-1);
			return;
		}

		// update packr vmargs
		PackrConfig.updateLauncherArgs(bootstrap);

		REPO_DIR.mkdirs();

		try
		{
			download(frame, bootstrap);
		}
		catch (IOException ex)
		{
			log.error("unable to download artifacts", ex);
			frame.setVisible(false);
			frame.dispose();
			System.exit(-1);
			return;
		}

		List<File> results = Arrays.stream(bootstrap.getArtifacts())
			.map(dep -> new File(REPO_DIR, dep.getName()))
			.collect(Collectors.toList());

		try
		{
			verifyJarHashes(bootstrap.getArtifacts());
			verify(results, CLIENT_MAIN_CLASS.replace('.', '/') + ".class");
		}
		catch (IOException | VerificationException | CertificateException ex)
		{
			log.error("Unable to verify artifacts", ex);
			frame.setVisible(false);
			frame.dispose();
			System.exit(-1);
			return;
		}

		frame.setVisible(false);
		frame.dispose();

		String clientArgs = getArgs(options);

		// packr doesn't let us specify command line arguments
		if ("true".equals(System.getProperty("runelite.launcher.nojvm")) || options.has("nojvm"))
		{
			try
			{
				ReflectionLauncher.launch(results, clientArgs);
			}
			catch (MalformedURLException ex)
			{
				log.error("unable to launch client", ex);
			}
		}
		else
		{
			try
			{
				JvmLauncher.launch(bootstrap, results, clientArgs, extraJvmParams, isDebug);
			}
			catch (IOException ex)
			{
				log.error("unable to launch client", ex);
			}
		}
	}

	private static Bootstrap getBootstrap() throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, VerificationException
	{
		URL u = new URL(CLIENT_BOOTSTRAP_URL);
		URL signatureUrl = new URL(CLIENT_BOOTSTRAP_SHA256_URL);

		URLConnection conn = u.openConnection();
		URLConnection signatureConn = signatureUrl.openConnection();

		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		signatureConn.setRequestProperty("User-Agent", "Mozilla/5.0");

		try (InputStream i = conn.getInputStream();
			InputStream signatureIn = signatureConn.getInputStream())
		{
			byte[] bytes = ByteStreams.toByteArray(i);
			byte[] signature = ByteStreams.toByteArray(signatureIn);

			Certificate certificate = getCertificate();
			Signature s = Signature.getInstance("SHA256withRSA");
			s.initVerify(certificate);
			s.update(bytes);

			if (!s.verify(signature))
			{
				throw new VerificationException("Unable to verify bootstrap signature");
			}

			Gson g = new Gson();
			return g.fromJson(new InputStreamReader(new ByteArrayInputStream(bytes)), Bootstrap.class);
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

	private static void download(LauncherFrame frame, Bootstrap bootstrap) throws IOException
	{
		Artifact[] artifacts = bootstrap.getArtifacts();
		for (Artifact artifact : artifacts)
		{
			File dest = new File(REPO_DIR, artifact.getName());

			String hash;
			try
			{
				hash = hash(dest);
			}
			catch (FileNotFoundException ex)
			{
				hash = null;
			}

			if (Objects.equals(hash, artifact.getHash()))
			{
				log.debug("Hash for {} up to date", artifact.getName());
				continue;
			}

			log.debug("Downloading {}", artifact.getName());

			URL url = new URL(artifact.getPath());
			URLConnection conn = url.openConnection();
			conn.setRequestProperty("User-Agent", "Mozilla/5.0");
			try (InputStream in = conn.getInputStream();
				FileOutputStream fout = new FileOutputStream(dest))
			{
				int i;
				int bytes = 0;
				byte[] buffer = new byte[1024 * 1024];
				while ((i = in.read(buffer)) != -1)
				{
					bytes += i;
					fout.write(buffer, 0, i);
					frame.progress(artifact.getName(), bytes, artifact.getSize());
				}
			}
		}
	}

	private static void verifyJarSignature(File jarFile) throws CertificateException, IOException
	{
		Certificate certificate = getCertificate();

		JarVerifier.verify(new JarFile(jarFile), certificate);
	}

	private static void verify(List<File> jarFiles, String mainClass) throws IOException, CertificateException, VerificationException
	{
		boolean verified = false;

		for (File jarFile : jarFiles)
		{
			JarFile jf = new JarFile(jarFile);
			Enumeration entries = jf.entries();

			while (entries.hasMoreElements())
			{
				JarEntry je = (JarEntry) entries.nextElement();

				if (je.getName().equals(mainClass))
				{
					verifyJarSignature(jarFile);
					verified = true;
					break;
				}
			}
		}

		if (!verified)
		{
			throw new VerificationException("No artifact matches main class");
		}
	}

	private static void verifyJarHashes(Artifact[] artifacts) throws VerificationException
	{
		for (Artifact artifact : artifacts)
		{
			String expectedHash = artifact.getHash();
			String fileHash;
			try
			{
				fileHash = hash(new File(REPO_DIR, artifact.getName()));
			}
			catch (IOException e)
			{
				throw new VerificationException("unable to hash file", e);
			}

			if (!fileHash.equals(expectedHash))
			{
				log.warn("Expected {} for {} but got {}", expectedHash, artifact.getName(), fileHash);
				throw new VerificationException("Expected " + expectedHash + " for " + artifact.getName() + " but got " + fileHash);
			}

			log.info("Verified hash of {}", artifact.getName());
		}
	}

	private static String hash(File file) throws IOException
	{
		HashFunction sha256 = Hashing.sha256();
		return Files.asByteSource(file).hash(sha256).toString();
	}

	private static Certificate getCertificate() throws CertificateException
	{
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		Certificate certificate = certFactory.generateCertificate(JarVerifier.class.getResourceAsStream("/runelite.crt"));
		return certificate;
	}
}

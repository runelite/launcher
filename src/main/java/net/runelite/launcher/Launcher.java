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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.vdurmont.semver4j.Semver;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.JButton;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Artifact;
import net.runelite.launcher.beans.Bootstrap;
import org.slf4j.LoggerFactory;

@Slf4j
public class Launcher
{
	private static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	private static final File LOGS_DIR = new File(RUNELITE_DIR, "logs");
	private static final File REPO_DIR = new File(RUNELITE_DIR, "repository2");
	private static final File CRASH_FILES = new File(LOGS_DIR, "jvm_crash_pid_%p.log");
	static final String LAUNCHER_BUILD = "https://raw.githubusercontent.com/open-osrs/launcher/master/build.gradle.kts";
	private static final String CLIENT_BOOTSTRAP_STAGING_URL = "https://raw.githubusercontent.com/open-osrs/hosting/master/bootstrap-staging.json";
	private static final String CLIENT_BOOTSTRAP_STABLE_URL = "https://raw.githubusercontent.com/open-osrs/hosting/master/bootstrap-stable.json";
	private static final String CLIENT_BOOTSTRAP_NIGHTLY_URL = "https://raw.githubusercontent.com/open-osrs/hosting/master/bootstrap-nightly.json";
	static final String USER_AGENT = "OpenOSRS/" + LauncherProperties.getVersion();
	private static final boolean enforceDependencyHashing = true;
	private static boolean nightly = false;
	private static boolean staging = false;
	private static boolean stable = false;

	static final String CLIENT_MAIN_CLASS = "net.runelite.client.RuneLite";

	public static void main(String[] args)
	{
		Properties prop = new Properties();

		try
		{
			prop.load(new FileInputStream(new File(RUNELITE_DIR, "runeliteplus.properties")));
		}
		catch (IOException ignored)
		{
		}

		boolean askmode = Optional.ofNullable(prop.getProperty("openosrs.askMode")).map(Boolean::valueOf).orElse(true);
		String bootstrapMode = prop.getProperty("openosrs.bootstrapMode");
		boolean disableHw = Boolean.parseBoolean(prop.getProperty("openosrs.disableHw"));

		OptionParser parser = new OptionParser();
		parser.accepts("clientargs").withRequiredArg();
		parser.accepts("nojvm");
		parser.accepts("forcejvm");
		parser.accepts("debug");
		parser.accepts("nightly");
		parser.accepts("staging");
		parser.accepts("stable");

		HardwareAccelerationMode defaultMode;

		if (disableHw)
		{
			defaultMode = HardwareAccelerationMode.OFF;
		}
		else
		{
			switch (OS.getOs())
			{
				case Windows:
					defaultMode = HardwareAccelerationMode.DIRECTDRAW;
					break;
				case MacOS:
					defaultMode = HardwareAccelerationMode.OPENGL;
					break;
				case Linux:
				default:
					defaultMode = HardwareAccelerationMode.OFF;
					break;
			}
		}

		// Create typed argument for the hardware acceleration mode
		final ArgumentAcceptingOptionSpec<HardwareAccelerationMode> mode = parser.accepts("mode")
			.withRequiredArg()
			.ofType(HardwareAccelerationMode.class)
			.defaultsTo(defaultMode);

		OptionSet options = parser.parse(args);

		if (!askmode)
		{
			if (bootstrapMode.equals("STABLE"))
			{
				stable = true;
			}
			else if (bootstrapMode.equals("NIGHTLY"))
			{
				nightly = true;
			}
		}

		nightly |= options.has("nightly");
		staging = options.has("staging");
		stable |= options.has("stable");

		LOGS_DIR.mkdirs();

		final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

		if (options.has("debug"))
		{
			logger.setLevel(Level.DEBUG);
		}

		if (!nightly && !staging && !stable)
		{
			OpenOSRSSplashScreen.init(null);
			OpenOSRSSplashScreen.barMessage(null);
			OpenOSRSSplashScreen.message(null);
			List<JButton> buttons = OpenOSRSSplashScreen.addButtons();

			if (buttons != null)
			{
				buttons.get(0).addActionListener(e -> {
					stable = true;
					OpenOSRSSplashScreen.close();
					Runnable task = () -> launch(mode, options, prop);
					Thread thread = new Thread(task);
					thread.start();
				});

				buttons.get(1).addActionListener(e -> {
					nightly = true;
					OpenOSRSSplashScreen.close();
					Runnable task = () -> launch(mode, options, prop);
					Thread thread = new Thread(task);
					thread.start();
				});
			}
		}
		else
		{
			launch(mode, options, prop);
		}
	}

	private static void launch(ArgumentAcceptingOptionSpec<HardwareAccelerationMode> mode, OptionSet options, Properties prop)
	{
		try
		{
			OpenOSRSSplashScreen.init(nightly ? "Nightly" : stable ? "Stable" : "Staging");
			OpenOSRSSplashScreen.stage(0, "Setting up environment");

			log.info("OpenOSRS Launcher version {}", LauncherProperties.getVersion());
			// Print out system info
			log.debug("Java Environment:");
			final Properties p = System.getProperties();
			final Enumeration keys = p.keys();

			while (keys.hasMoreElements())
			{
				final String key = (String) keys.nextElement();
				final String value = (String) p.get(key);
				log.debug("  {}: {}", key, value);
			}

			// Get hardware acceleration mode
			final HardwareAccelerationMode hardwareAccelerationMode = options.valueOf(mode);
			log.info("Setting hardware acceleration to {}", hardwareAccelerationMode);

			// Enable hardware acceleration
			final List<String> extraJvmParams = hardwareAccelerationMode.toParams();

			// Always use IPv4 over IPv6
			extraJvmParams.add("-Djava.net.preferIPv4Stack=true");
			extraJvmParams.add("-Djava.net.preferIPv4Addresses=true");

			// Stream launcher version
			extraJvmParams.add("-D" + LauncherProperties.getVersionKey() + "=" + LauncherProperties.getVersion());

			// Set all JVM params
			setJvmParams(extraJvmParams);

			// Set hs_err_pid location (do this after setJvmParams because it can't be set at runtime)
			log.debug("Setting JVM crash log location to {}", CRASH_FILES);
			extraJvmParams.add("-XX:ErrorFile=" + CRASH_FILES.getAbsolutePath());

			OpenOSRSSplashScreen.stage(.05, "Downloading bootstrap");
			Bootstrap bootstrap;
			try
			{
				bootstrap = getBootstrap();
			}
			catch (IOException ex)
			{
				log.error("error fetching bootstrap", ex);
				OpenOSRSSplashScreen.setError("Error while downloading the bootstrap!", "You have encountered an issue, please check your log files for a more detailed error message.");
				return;
			}

			OpenOSRSSplashScreen.stage(.10, "Tidying the cache");

			boolean launcherTooOld = bootstrap.getRequiredLauncherVersion() != null &&
				compareVersion(bootstrap.getRequiredLauncherVersion(), LauncherProperties.getVersion()) > 0;

			boolean jvmTooOld = false;
			try
			{
				if (bootstrap.getRequiredJVMVersion() != null)
				{
					jvmTooOld = Runtime.Version.parse(bootstrap.getRequiredJVMVersion())
						.compareTo(Runtime.version()) > 0;
				}
			}
			catch (IllegalArgumentException e)
			{
				log.warn("Unable to parse bootstrap version", e);
			}

			boolean nojvm = Boolean.parseBoolean(prop.getProperty("openosrs.noJvm")) || "true".equals(System.getProperty("runelite.launcher.nojvm")) || "true".equals(System.getProperty("openosrs.launcher.nojvm"));

			if (launcherTooOld || (nojvm && jvmTooOld))
			{
				OpenOSRSSplashScreen.setError("Error while downloading the client!", "You have encountered an issue, please check your log files for a more detailed error message.");
				return;
			}
			if (jvmTooOld)
			{
				OpenOSRSSplashScreen.setError("Your Java installation is too old", "OpenOSRS now requires Java " +
					bootstrap.getRequiredJVMVersion() + " to run. You can get a platform specific version from openosrs.com," +
					" or install a newer version of Java.");
				return;
			}

			if (!checkVersion(bootstrap))
			{
				log.error("launcher version too low");
				OpenOSRSSplashScreen.setError("Your launcher is outdated!", "The launcher you're using is oudated. Please either download a newer version from openosrs.com or by clicking the update button on the right hand side.");
				return;
			}

			// update packr vmargs
			PackrConfig.updateLauncherArgs(bootstrap, extraJvmParams);

			REPO_DIR.mkdirs();

			// Clean out old artifacts from the repository
			clean(bootstrap.getArtifacts());

			try
			{
				download(bootstrap);
			}
			catch (IOException ex)
			{
				log.error("unable to download artifacts", ex);
				OpenOSRSSplashScreen.setError("Error while downloading the client!", "You have encountered an issue, please check your log files for a more detailed error message.");
				return;
			}

			List<File> results = Arrays.stream(bootstrap.getArtifacts())
				.map(dep -> new File(REPO_DIR, dep.getName()))
				.collect(Collectors.toList());

			OpenOSRSSplashScreen.stage(.80, "Verifying");
			try
			{
				verifyJarHashes(bootstrap.getArtifacts());
			}
			catch (VerificationException ex)
			{
				log.error("Unable to verify artifacts", ex);
				OpenOSRSSplashScreen.setError("Error while verifying downloaded files!", "You have encountered an issue, please check your log files for a more detailed error message.");
				return;
			}

			final Collection<String> clientArgs = getClientArgs(options);

			if (log.isDebugEnabled())
			{
				clientArgs.add("--debug");
			}

			if (Boolean.parseBoolean(prop.getProperty("openosrs.useProxy")))
			{
				clientArgs.add("--proxy " + prop.getProperty("openosrs.proxyDetails"));
			}

			OpenOSRSSplashScreen.stage(.90, "Starting the client");
			OpenOSRSSplashScreen.close();

			// packr doesn't let us specify command line arguments
			if (!options.has("forcejvm") && nojvm || options.has("nojvm"))
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
					JvmLauncher.launch(bootstrap, results, clientArgs, extraJvmParams);
					OpenOSRSSplashScreen.close();
				}
				catch (IOException ex)
				{
					log.error("unable to launch client", ex);
				}
			}
		}
		catch (Exception e)
		{
			log.error("Failure during startup", e);
			OpenOSRSSplashScreen.setError("OpenOSRS has encountered an unexpected error during startup!", "You have encountered an issue, please check your log files for a more detailed error message.");

		}
		catch (Error e)
		{
			// packr seems to eat exceptions thrown out of main, so at least try to log it
			log.error("Failure during startup", e);
			OpenOSRSSplashScreen.setError("OpenOSRS has encountered an unexpected error during startup!", "You have encountered an issue, please check your log files for a more detailed error message.");

			throw e;
		}
	}

	private static boolean checkVersion(Bootstrap bootstrap)
	{
		if (bootstrap.getMinimumLauncherVersion() == null || LauncherProperties.getVersion() == null)
		{
			return true;
		}
		Semver minimum = new Semver(bootstrap.getMinimumLauncherVersion()).withClearedSuffixAndBuild();
		Semver ours = new Semver(LauncherProperties.getVersion()).withClearedSuffixAndBuild();
		return !ours.isLowerThan(minimum);
	}

	private static void setJvmParams(final Collection<String> params)
	{
		for (String param : params)
		{
			final String[] split = param.replace("-D", "").split("=");
			System.setProperty(split[0], split[1]);
		}
	}

	private static Bootstrap getBootstrap() throws IOException
	{
		URL u;
		if (stable)
		{
			u = new URL(CLIENT_BOOTSTRAP_STABLE_URL);
		}
		else if (nightly)
		{
			u = new URL(CLIENT_BOOTSTRAP_NIGHTLY_URL);
		}
		else if (staging)
		{
			u = new URL(CLIENT_BOOTSTRAP_STAGING_URL);
		}
		else
		{
			throw new RuntimeException("How did we get here?");
		}

		log.info(String.valueOf(u));

		URLConnection conn = u.openConnection();

		conn.setRequestProperty("User-Agent", USER_AGENT);

		try (InputStream i = conn.getInputStream())
		{
			byte[] bytes = ByteStreams.toByteArray(i);

			Gson g = new Gson();
			return g.fromJson(new InputStreamReader(new ByteArrayInputStream(bytes)), Bootstrap.class);
		}
	}

	private static Collection<String> getClientArgs(OptionSet options)
	{
		String clientArgs = System.getenv("OPENOSRS_ARGS");
		if (options.has("clientargs"))
		{
			clientArgs = (String) options.valueOf("clientargs");
		}
		return !Strings.isNullOrEmpty(clientArgs)
			? new ArrayList<>(Splitter.on(' ').omitEmptyStrings().trimResults().splitToList(clientArgs))
			: new ArrayList<>();
	}

	private static void download(Bootstrap bootstrap) throws IOException
	{
		Artifact[] artifacts = bootstrap.getArtifacts();
		List<Artifact> toDownload = new ArrayList<>(artifacts.length);
		int totalDownloadBytes = 0;

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

			toDownload.add(artifact);
			totalDownloadBytes += artifact.getSize();
		}

		final double START_PROGRESS = .15;
		int downloaded = 0;
		OpenOSRSSplashScreen.stage(START_PROGRESS, "Downloading");

		for (Artifact artifact : toDownload)
		{
			File dest = new File(REPO_DIR, artifact.getName());

			log.debug("Downloading {}", artifact.getName());

			URL url = new URL(artifact.getPath());
			URLConnection conn = url.openConnection();
			conn.setRequestProperty("User-Agent", USER_AGENT);
			try (InputStream in = conn.getInputStream();
				FileOutputStream fout = new FileOutputStream(dest))
			{
				int i;
				byte[] buffer = new byte[1024 * 1024];
				while ((i = in.read(buffer)) != -1)
				{
					fout.write(buffer, 0, i);
					downloaded += i;
					OpenOSRSSplashScreen.stage(START_PROGRESS, .80, artifact.getName(), downloaded, totalDownloadBytes, true);
				}
			}
		}
	}

	private static void clean(Artifact[] artifacts)
	{
		File[] existingFiles = REPO_DIR.listFiles();

		if (existingFiles == null)
		{
			return;
		}

		Set<String> artifactNames = Arrays.stream(artifacts)
			.map(Artifact::getName)
			.collect(Collectors.toSet());

		for (File file : existingFiles)
		{
			if (file.isFile() && !artifactNames.contains(file.getName()))
			{
				if (file.delete())
				{
					log.debug("Deleted old artifact {}", file);
				}
				else
				{
					log.warn("Unable to delete old artifact {}", file);
				}
			}
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

				//if (enforceDependencyHashing)
				//throw new VerificationException("Expected " + expectedHash + " for " + artifact.getName() + " but got " + fileHash);
			}

			log.info("Verified hash of {}", artifact.getName());
		}
	}

	private static String hash(File file) throws IOException
	{
		HashFunction sha256 = Hashing.sha256();
		return Files.asByteSource(file).hash(sha256).toString();
	}

	private static int compareVersion(String a, String b)
	{
		Pattern tok = Pattern.compile("[^0-9a-zA-Z]");
		return Arrays.compare(tok.split(a), tok.split(b), (x, y) ->
		{
			Integer ix = null;
			try
			{
				ix = Integer.parseInt(x);
			}
			catch (NumberFormatException ignored)
			{
			}

			Integer iy = null;
			try
			{
				iy = Integer.parseInt(y);
			}
			catch (NumberFormatException ignored)
			{
			}

			if (ix == null && iy == null)
			{
				return x.compareToIgnoreCase(y);
			}

			if (ix == null)
			{
				return -1;
			}
			if (iy == null)
			{
				return 1;
			}

			return ix.compareTo(iy);

		});
	}
}

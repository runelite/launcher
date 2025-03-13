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
import com.google.archivepatcher.applier.FileByFileV1DeltaApplier;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import javax.swing.SwingUtilities;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Artifact;
import net.runelite.launcher.beans.Bootstrap;
import net.runelite.launcher.beans.Diff;
import net.runelite.launcher.beans.Platform;
import org.slf4j.LoggerFactory;

@Slf4j
public class Launcher
{
	static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	static final File LOGS_DIR = new File(RUNELITE_DIR, "logs");
	static final File REPO_DIR = new File(RUNELITE_DIR, "repository2");
	public static final File CRASH_FILES = new File(LOGS_DIR, "jvm_crash_pid_%p.log");
	private static final String USER_AGENT = "RuneLite/" + LauncherProperties.getVersion();
	static final String LAUNCHER_EXECUTABLE_NAME_WIN = "RuneLite.exe";
	static final String LAUNCHER_EXECUTABLE_NAME_OSX = "RuneLite";
	static boolean nativesLoaded;

	private static HttpClient httpClient;

	private static OptionSet parseArgs(String[] args)
	{
		args = parseApplicationURI(args);

		OptionParser parser = new OptionParser(false);
		parser.allowsUnrecognizedOptions();
		parser.accepts("postinstall", "Perform post-install tasks");
		parser.accepts("debug", "Enable debug logging");
		parser.accepts("nodiff", "Always download full artifacts instead of diffs");
		parser.accepts("insecure-skip-tls-verification", "Disable TLS certificate and hostname verification");
		parser.accepts("scale", "Custom scale factor for Java 2D").withRequiredArg();
		parser.accepts("noupdate", "Skips the launcher self-update");
		parser.accepts("help", "Show this text (use -- --help for client help)").forHelp();
		parser.accepts("classpath", "Classpath for the client").withRequiredArg();
		parser.accepts("J", "JVM argument (FORK or JVM launch mode only)").withRequiredArg();
		parser.accepts("configure", "Opens configuration GUI");
		parser.accepts("launch-mode", "JVM launch method (JVM, FORK, REFLECT)")
			.withRequiredArg()
			.ofType(LaunchMode.class);
		parser.accepts("hw-accel", "Java 2D hardware acceleration mode (OFF, DIRECTDRAW, OPENGL, METAL)")
			.withRequiredArg()
			.ofType(HardwareAccelerationMode.class);
		parser.accepts("mode", "Alias of hw-accel")
			.withRequiredArg()
			.ofType(HardwareAccelerationMode.class);

		if (OS.getOs() == OS.OSType.MacOS)
		{
			// Parse macos PSN, eg: -psn_0_352342
			parser.accepts("p").withRequiredArg();
		}

		final OptionSet options;
		try
		{
			options = parser.parse(args);
		}
		catch (OptionException ex)
		{
			log.error("unable to parse arguments", ex);
			SwingUtilities.invokeLater(() ->
				new FatalErrorDialog("RuneLite was unable to parse the provided application arguments: " + ex.getMessage())
					.open());
			throw ex;
		}

		if (options.has("help"))
		{
			try
			{
				parser.printHelpOn(System.out);
			}
			catch (IOException e)
			{
				log.error(null, e);
			}
			System.exit(0);
		}

		return options;
	}

	private static String[] parseApplicationURI(String[] args)
	{
		// runelite-jav://oldschool2.runescape.com:80/jav_config.ws
		if (args.length > 0 && args[0].startsWith("runelite-jav://"))
		{
			log.info("Launched using URI {}", args[0]);
			return new String[]{
				"--jav_config", args[0].replace("runelite-jav", "http")
			};
		}

		return args;
	}

	public static void main(String[] args)
	{
		final OptionSet options = parseArgs(args);

		if (options.has("configure"))
		{
			ConfigurationFrame.open();
			return;
		}

		final LauncherSettings settings = LauncherSettings.loadSettings();
		settings.apply(options);

		final boolean postInstall = options.has("postinstall");

		// Setup logging
		LOGS_DIR.mkdirs();
		if (settings.isDebug())
		{
			final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
			logger.setLevel(Level.DEBUG);
		}

		initDll();

		// RTSS triggers off of the CreateWindow event, so this needs to be in place early, prior to splash screen
		initDllBlacklist();

		try
		{
			if (options.has("classpath"))
			{
				TrustManagerUtil.setupTrustManager();

				// being called from ForkLauncher. All JVM options are already set.
				var classpathOpt = String.valueOf(options.valueOf("classpath"));
				var classpath = Streams.stream(Splitter.on(File.pathSeparatorChar)
					.split(classpathOpt))
					.map(name -> new File(REPO_DIR, name))
					.collect(Collectors.toList());
				try
				{
					ReflectionLauncher.launch(classpath, getClientArgs(settings));
				}
				catch (Exception e)
				{
					log.error("error launching client", e);
				}
				return;
			}

			final Map<String, String> jvmProps = new LinkedHashMap<>();
			if (settings.scale != null)
			{
				// This calls SetProcessDPIAware(). Since the RuneLite.exe manifest is DPI unaware
				// Windows will scale the application if this isn't called. Thus the default scaling
				// mode is Windows scaling due to being DPI unaware.
				// https://docs.microsoft.com/en-us/windows/win32/hidpi/high-dpi-desktop-application-development-on-windows
				jvmProps.put("sun.java2d.dpiaware", "true");
				// This sets the Java 2D scaling factor, overriding the default behavior of detecting the scale via
				// GetDpiForMonitor.
				jvmProps.put("sun.java2d.uiScale", Double.toString(settings.scale));
			}

			final var hardwareAccelMode = settings.hardwareAccelerationMode == HardwareAccelerationMode.AUTO ?
				HardwareAccelerationMode.defaultMode(OS.getOs()) : settings.hardwareAccelerationMode;
			jvmProps.putAll(hardwareAccelMode.toParams(OS.getOs()));

			// As of JDK-8243269 (11.0.8) and JDK-8235363 (14), AWT makes macOS dark mode support opt-in so interfaces
			// with hardcoded foreground/background colours don't get broken by system settings. Considering the native
			// Aqua we draw consists a window border and an about box, it's safe to say we can opt in.
			if (OS.getOs() == OS.OSType.MacOS)
			{
				jvmProps.put("apple.awt.application.appearance", "system");
			}

			// Stream launcher version
			jvmProps.put(LauncherProperties.getVersionKey(), LauncherProperties.getVersion());

			if (settings.isSkipTlsVerification())
			{
				jvmProps.put("runelite.insecure-skip-tls-verification", "true");
			}

			log.info("RuneLite Launcher version {}", LauncherProperties.getVersion());
			log.info("Launcher configuration:" + System.lineSeparator() + "{}", settings.configurationStr());
			log.info("OS name: {}, version: {}, arch: {}", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
			log.info("Using hardware acceleration mode: {}", hardwareAccelMode);

			// java2d properties have to be set prior to the graphics environment startup
			setJvmParams(jvmProps);

			if (settings.isSkipTlsVerification())
			{
				TrustManagerUtil.setupInsecureTrustManager();
				// This is the only way to disable hostname verification with HttpClient - https://stackoverflow.com/a/52995420
				System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
			}
			else
			{
				TrustManagerUtil.setupTrustManager();
			}

			// setup http client after the default SSLContext is set
			httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.ALWAYS)
				.build();

			if (postInstall)
			{
				postInstall(settings);
				return;
			}

			SplashScreen.init();
			SplashScreen.stage(0, "Preparing", "Setting up environment");

			// Print out system info
			if (log.isDebugEnabled())
			{
				final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

				log.debug("Command line arguments: {}", String.join(" ", args));
				// This includes arguments from _JAVA_OPTIONS, which are parsed after command line flags and applied to
				// the global VM args
				log.debug("Java VM arguments: {}", String.join(" ", runtime.getInputArguments()));
				log.debug("Java Environment:");
				final Properties p = System.getProperties();
				final Enumeration<Object> keys = p.keys();

				while (keys.hasMoreElements())
				{
					final String key = (String) keys.nextElement();
					final String value = (String) p.get(key);
					log.debug("  {}: {}", key, value);
				}
			}

			// fix up permissions before potentially removing the RUNASADMIN compat key
			if (FilesystemPermissions.check())
			{
				// check() opens an error dialog
				return;
			}

			if (JagexLauncherCompatibility.check())
			{
				// check() opens an error dialog
				return;
			}

			if (!REPO_DIR.exists() && !REPO_DIR.mkdirs())
			{
				log.error("unable to create directory {}", REPO_DIR);
				SwingUtilities.invokeLater(() -> new FatalErrorDialog("Unable to create RuneLite directory " + REPO_DIR.getAbsolutePath() + ". Check your filesystem permissions are correct.").open());
				return;
			}

			SplashScreen.stage(.05, null, "Downloading bootstrap");
			Bootstrap bootstrap;
			try
			{
				bootstrap = getBootstrap();
			}
			catch (IOException | VerificationException | CertificateException | SignatureException | InvalidKeyException | NoSuchAlgorithmException ex)
			{
				log.error("error fetching bootstrap", ex);

				String extract = CertPathExtractor.extract(ex);
				if (extract != null)
				{
					log.error("untrusted certificate chain: {}", extract);
				}

				SwingUtilities.invokeLater(() -> FatalErrorDialog.showNetErrorWindow("downloading the bootstrap", ex));
				return;
			}

			SplashScreen.stage(.07, null, "Checking for updates");

			Updater.update(bootstrap, settings, args);

			SplashScreen.stage(.10, null, "Tidying the cache");

			if (jvmOutdated(bootstrap))
			{
				// jvmOutdated opens an error dialog
				return;
			}

			// update packr vmargs to the launcher vmargs from bootstrap.
			PackrConfig.updateLauncherArgs(bootstrap, settings);

			// Determine artifacts for this OS
			List<Artifact> artifacts = Arrays.stream(bootstrap.getArtifacts())
				.filter(a ->
				{
					if (a.getPlatform() == null)
					{
						return true;
					}

					final String os = System.getProperty("os.name");
					final String arch = System.getProperty("os.arch");
					for (Platform platform : a.getPlatform())
					{
						if (platform.getName() == null)
						{
							continue;
						}

						OS.OSType platformOs = OS.parseOs(platform.getName());
						if ((platformOs == OS.OSType.Other ? platform.getName().equals(os) : platformOs == OS.getOs())
							&& (platform.getArch() == null || platform.getArch().equals(arch)))
						{
							return true;
						}
					}

					return false;
				})
				.collect(Collectors.toList());

			// Clean out old artifacts from the repository
			clean(artifacts);

			try
			{
				download(artifacts, settings.isNodiffs());
			}
			catch (IOException ex)
			{
				log.error("unable to download artifacts", ex);
				SwingUtilities.invokeLater(() -> FatalErrorDialog.showNetErrorWindow("downloading the client", ex));
				return;
			}

			SplashScreen.stage(.80, null, "Verifying");
			try
			{
				verifyJarHashes(artifacts);
			}
			catch (VerificationException ex)
			{
				log.error("Unable to verify artifacts", ex);
				SwingUtilities.invokeLater(() -> FatalErrorDialog.showNetErrorWindow("verifying downloaded files", ex));
				return;
			}

			final Collection<String> clientArgs = getClientArgs(settings);
			SplashScreen.stage(.90, "Starting the client", "");

			var classpath = artifacts.stream()
				.map(dep -> new File(REPO_DIR, dep.getName()))
				.collect(Collectors.toList());

			List<String> jvmParams = new ArrayList<>();
			// Set hs_err_pid location. This is a jvm param and can't be set at runtime.
			log.debug("Setting JVM crash log location to {}", CRASH_FILES);
			jvmParams.add("-XX:ErrorFile=" + CRASH_FILES.getAbsolutePath());
			// Add VM args from cli/env
			jvmParams.addAll(getJvmArgs(settings));

			if (settings.launchMode == LaunchMode.REFLECT)
			{
				log.debug("Using launch mode: REFLECT");
				ReflectionLauncher.launch(classpath, clientArgs);
			}
			else if (settings.launchMode == LaunchMode.FORK || (settings.launchMode == LaunchMode.AUTO && ForkLauncher.canForkLaunch()))
			{
				log.debug("Using launch mode: FORK");
				ForkLauncher.launch(bootstrap, classpath, clientArgs, jvmProps, jvmParams);
			}
			else
			{
				if (System.getenv("APPIMAGE") != null)
				{
					// java.home is in the appimage, so we can never use the jvm launcher
					throw new RuntimeException("JVM launcher is not supported from the appimage");
				}

				// launch mode JVM or AUTO outside of packr
				log.debug("Using launch mode: JVM");
				JvmLauncher.launch(bootstrap, classpath, clientArgs, jvmProps, jvmParams);
			}
		}
		catch (Exception e)
		{
			log.error("Failure during startup", e);
			if (!postInstall)
			{
				SwingUtilities.invokeLater(() ->
					new FatalErrorDialog("RuneLite has encountered an unexpected error during startup.")
						.open());
			}
		}
		catch (Error e)
		{
			// packr seems to eat exceptions thrown out of main, so at least try to log it
			log.error("Failure during startup", e);
			throw e;
		}
		finally
		{
			SplashScreen.stop();
		}
	}

	private static void setJvmParams(final Map<String, String> params)
	{
		for (Map.Entry<String, String> entry : params.entrySet())
		{
			System.setProperty(entry.getKey(), entry.getValue());
		}
	}

	private static Bootstrap getBootstrap() throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, VerificationException
	{
		HttpRequest bootstrapReq = HttpRequest.newBuilder()
			.uri(URI.create(LauncherProperties.getBootstrap()))
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();

		HttpRequest bootstrapSigReq = HttpRequest.newBuilder()
			.uri(URI.create(LauncherProperties.getBootstrapSig()))
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();

		HttpResponse<byte[]> bootstrapResp, bootstrapSigResp;

		try
		{
			bootstrapResp = httpClient.send(bootstrapReq, HttpResponse.BodyHandlers.ofByteArray());
			bootstrapSigResp = httpClient.send(bootstrapSigReq, HttpResponse.BodyHandlers.ofByteArray());
		}
		catch (InterruptedException ex)
		{
			throw new IOException(ex);
		}

		if (bootstrapResp.statusCode() != 200)
		{
			throw new IOException("Unable to download bootstrap (status code " + bootstrapResp.statusCode() + "): " + new String(bootstrapResp.body()));
		}

		if (bootstrapSigResp.statusCode() != 200)
		{
			throw new IOException("Unable to download bootstrap signature (status code " + bootstrapSigResp.statusCode() + "): " + new String(bootstrapSigResp.body()));
		}

		final byte[] bytes = bootstrapResp.body();
		final byte[] signature = bootstrapSigResp.body();

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

	private static boolean jvmOutdated(Bootstrap bootstrap)
	{
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

		if (launcherTooOld)
		{
			SwingUtilities.invokeLater(() ->
				new FatalErrorDialog("Your launcher is too old to start RuneLite. Please download and install a more " +
					"recent one from RuneLite.net.")
					.addButton("RuneLite.net", () -> LinkBrowser.browse(LauncherProperties.getDownloadLink()))
					.open());
			return true;
		}
		if (jvmTooOld)
		{
			SwingUtilities.invokeLater(() ->
				new FatalErrorDialog("Your Java installation is too old. RuneLite now requires Java " +
					bootstrap.getRequiredJVMVersion() + " to run. You can get a platform specific version from RuneLite.net," +
					" or install a newer version of Java.")
					.addButton("RuneLite.net", () -> LinkBrowser.browse(LauncherProperties.getDownloadLink()))
					.open());
			return true;
		}

		return false;
	}

	private static Collection<String> getClientArgs(LauncherSettings settings)
	{
		final var args = new ArrayList<>(settings.clientArguments);

		String clientArgs = System.getenv("RUNELITE_ARGS");
		if (!Strings.isNullOrEmpty(clientArgs))
		{
			args.addAll(Splitter.on(' ')
				.omitEmptyStrings()
				.trimResults()
				.splitToList(clientArgs));
		}

		if (settings.debug)
		{
			args.add("--debug");
		}

		if (settings.safemode)
		{
			args.add("--safe-mode");
		}

		return args;
	}

	private static List<String> getJvmArgs(LauncherSettings settings)
	{
		var args = new ArrayList<>(settings.jvmArguments);

		if (settings.ipv4)
		{
			args.add("-Djava.net.preferIPv4Stack=true");
		}

		var envArgs = System.getenv("RUNELITE_VMARGS");
		if (!Strings.isNullOrEmpty(envArgs))
		{
			args.addAll(Splitter.on(' ')
				.omitEmptyStrings()
				.trimResults()
				.splitToList(envArgs));
		}

		return args;
	}

	private static void download(List<Artifact> artifacts, boolean nodiff) throws IOException
	{
		List<Artifact> toDownload = new ArrayList<>(artifacts.size());
		Map<Artifact, Diff> diffs = new HashMap<>();
		int totalDownloadBytes = 0;
		final boolean isCompatible = new DefaultDeflateCompatibilityWindow().isCompatible();

		if (!isCompatible && !nodiff)
		{
			log.debug("System zlib is not compatible with archive-patcher; not using diffs");
			nodiff = true;
		}

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
			catch (IOException ex)
			{
				dest.delete();
				hash = null;
			}

			if (Objects.equals(hash, artifact.getHash()))
			{
				log.debug("Hash for {} up to date", artifact.getName());
				continue;
			}

			int downloadSize = artifact.getSize();

			// See if there is a diff available
			if (!nodiff && artifact.getDiffs() != null)
			{
				for (Diff diff : artifact.getDiffs())
				{
					File old = new File(REPO_DIR, diff.getFrom());

					String oldhash;
					try
					{
						oldhash = hash(old);
					}
					catch (IOException ex)
					{
						continue;
					}

					// Check if old file is valid
					if (diff.getFromHash().equals(oldhash))
					{
						diffs.put(artifact, diff);
						downloadSize = diff.getSize();
					}
				}
			}

			toDownload.add(artifact);
			totalDownloadBytes += downloadSize;
		}

		final double START_PROGRESS = .15;
		int downloaded = 0;
		SplashScreen.stage(START_PROGRESS, "Downloading", "");

		for (Artifact artifact : toDownload)
		{
			File dest = new File(REPO_DIR, artifact.getName());
			final int total = downloaded;

			// Check if there is a diff we can download instead
			Diff diff = diffs.get(artifact);
			if (diff != null)
			{
				log.debug("Downloading diff {}", diff.getName());

				try
				{
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					final int totalBytes = totalDownloadBytes;
					download(diff.getPath(), diff.getHash(), (completed) ->
						SplashScreen.stage(START_PROGRESS, .80, null, diff.getName(), total + completed, totalBytes, true),
						out);
					downloaded += diff.getSize();

					File old = new File(REPO_DIR, diff.getFrom());
					HashCode hash;
					try (InputStream patchStream = new GZIPInputStream(new ByteArrayInputStream(out.toByteArray()));
						HashingOutputStream fout = new HashingOutputStream(Hashing.sha256(), Files.newOutputStream(dest.toPath())))
					{
						new FileByFileV1DeltaApplier().applyDelta(old, patchStream, fout);
						hash = fout.hash();
					}

					if (artifact.getHash().equals(hash.toString()))
					{
						log.debug("Patching successful for {}", artifact.getName());
						continue;
					}

					log.debug("Patched artifact hash mismatches! {}: got {} expected {}", artifact.getName(), hash.toString(), artifact.getHash());
				}
				catch (IOException | VerificationException e)
				{
					log.warn("unable to download patch {}", diff.getName(), e);
					// Fall through and try downloading the full artifact
				}

				// Adjust the download size for the difference
				totalDownloadBytes -= diff.getSize();
				totalDownloadBytes += artifact.getSize();
			}

			log.debug("Downloading {}", artifact.getName());

			try (OutputStream fout = Files.newOutputStream(dest.toPath()))
			{
				final int totalBytes = totalDownloadBytes;
				download(artifact.getPath(), artifact.getHash(), (completed) ->
					SplashScreen.stage(START_PROGRESS, .80, null, artifact.getName(), total + completed, totalBytes, true),
					fout);
				downloaded += artifact.getSize();
			}
			catch (VerificationException e)
			{
				log.warn("unable to verify jar {}", artifact.getName(), e);
			}
		}
	}

	private static void clean(List<Artifact> artifacts)
	{
		File[] existingFiles = REPO_DIR.listFiles();

		if (existingFiles == null)
		{
			return;
		}

		Set<String> artifactNames = new HashSet<>();
		for (Artifact artifact : artifacts)
		{
			artifactNames.add(artifact.getName());
			if (artifact.getDiffs() != null)
			{
				// Keep around the old files which diffs are from
				for (Diff diff : artifact.getDiffs())
				{
					artifactNames.add(diff.getFrom());
				}
			}
		}

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

	private static void verifyJarHashes(List<Artifact> artifacts) throws VerificationException
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
		return com.google.common.io.Files.asByteSource(file).hash(sha256).toString();
	}

	private static Certificate getCertificate() throws CertificateException
	{
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		Certificate certificate = certFactory.generateCertificate(Launcher.class.getResourceAsStream("runelite.crt"));
		return certificate;
	}

	static int compareVersion(String a, String b)
	{
		Pattern tok = Pattern.compile("[^0-9a-zA-Z]");
		return Arrays.compare(tok.split(a), tok.split(b), (x, y) ->
		{
			Integer ix = null;
			try
			{
				ix = Integer.parseInt(x);
			}
			catch (NumberFormatException e)
			{
			}

			Integer iy = null;
			try
			{
				iy = Integer.parseInt(y);
			}
			catch (NumberFormatException e)
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

			if (ix > iy)
			{
				return 1;
			}
			if (ix < iy)
			{
				return -1;
			}

			return 0;
		});
	}

	static void download(String path, String hash, IntConsumer progress, OutputStream out) throws IOException, VerificationException
	{
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(path))
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();

		HttpResponse<InputStream> response;
		try
		{
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		}
		catch (InterruptedException ex)
		{
			throw new IOException(ex);
		}

		if (response.statusCode() != 200)
		{
			throw new IOException("Unable to download " + path + " (status code " + response.statusCode() + ")");
		}

		int downloaded = 0;
		HashingOutputStream hout = new HashingOutputStream(Hashing.sha256(), out);
		try (InputStream in = response.body())
		{
			int i;
			byte[] buffer = new byte[1024 * 1024];
			while ((i = in.read(buffer)) != -1)
			{
				hout.write(buffer, 0, i);
				downloaded += i;
				progress.accept(downloaded);
			}
		}

		HashCode hashCode = hout.hash();
		if (!hash.equals(hashCode.toString()))
		{
			throw new VerificationException("Unable to verify resource " + path + " - expected " + hash + " got " + hashCode.toString());
		}
	}

	static boolean isJava17()
	{
		// 16 has the same module restrictions as 17, so we'll use the 17 settings for it
		return Runtime.version().feature() >= 16;
	}

	private static void postInstall(LauncherSettings settings)
	{
		Bootstrap bootstrap;
		try
		{
			bootstrap = getBootstrap();
		}
		catch (IOException | VerificationException | CertificateException | SignatureException | InvalidKeyException | NoSuchAlgorithmException ex)
		{
			log.error("error fetching bootstrap", ex);
			return;
		}

		PackrConfig.updateLauncherArgs(bootstrap, settings);

		log.info("Performed postinstall steps");
	}

	private static void initDll()
	{
		if (OS.getOs() != OS.OSType.Windows)
		{
			return;
		}

		String arch = System.getProperty("os.arch");
		if (!Set.of("x86", "amd64", "aarch64").contains(arch))
		{
			log.debug("System architecture is not supported for launcher natives: {}", arch);
			return;
		}

		try
		{
			System.loadLibrary("launcher_" + arch);
			log.debug("Loaded launcher native launcher_{}", arch);
			nativesLoaded = true;
		}
		catch (Error ex)
		{
			log.debug("Error loading launcher native", ex);
		}
	}

	private static void initDllBlacklist()
	{
		String blacklistedDlls = System.getProperty("runelite.launcher.blacklistedDlls");
		if (blacklistedDlls == null || blacklistedDlls.isEmpty())
		{
			return;
		}

		String[] dlls = blacklistedDlls.split(",");

		try
		{
			log.debug("Setting blacklisted dlls: {}", blacklistedDlls);
			setBlacklistedDlls(dlls);
		}
		catch (UnsatisfiedLinkError ex)
		{
			log.debug("Error setting dll blacklist", ex);
		}
	}

	private static native void setBlacklistedDlls(String[] dlls);

	static native String regQueryString(String subKey, String value);

	// Requires elevated permissions. Current valid inputs for key are: "HKCU" and "HKLM"
	static native boolean regDeleteValue(String key, String subKey, String value);

	static native boolean isProcessElevated(long pid);

	static native void setFileACL(String folder, String[] sids);
	static native String getUserSID();

	static native long runas(String path, String args);
}

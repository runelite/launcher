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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.SwingUtilities;
import joptsimple.ArgumentAcceptingOptionSpec;
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
	private static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	public static final File LOGS_DIR = new File(RUNELITE_DIR, "logs");
	private static final File REPO_DIR = new File(RUNELITE_DIR, "repository2");
	public static final File CRASH_FILES = new File(LOGS_DIR, "jvm_crash_pid_%p.log");
	private static final String USER_AGENT = "RuneLite/" + LauncherProperties.getVersion();

	public static void main(String[] args)
	{
		OptionParser parser = new OptionParser();
		parser.allowsUnrecognizedOptions();
		parser.accepts("postinstall", "Perform post-install tasks");
		parser.accepts("clientargs", "Arguments passed to the client").withRequiredArg();
		parser.accepts("nojvm", "Launch the client in this VM instead of launching a new VM");
		parser.accepts("debug", "Enable debug logging");
		parser.accepts("nodiff", "Always download full artifacts instead of diffs");
		parser.accepts("insecure-skip-tls-verification", "Disable TLS certificate and hostname verification");
		parser.accepts("scale", "Custom scale factor for Java 2D").withRequiredArg();
		parser.accepts("help", "Show this text (use --clientargs --help for client help)").forHelp();

		if (OS.getOs() == OS.OSType.MacOS)
		{
			parser.accepts("psn").withRequiredArg();
		}

		// Create typed argument for the hardware acceleration mode
		final ArgumentAcceptingOptionSpec<HardwareAccelerationMode> mode = parser.accepts("mode")
			.withRequiredArg()
			.ofType(HardwareAccelerationMode.class)
			.defaultsTo(HardwareAccelerationMode.defaultMode(OS.getOs()));

		final OptionSet options;
		final HardwareAccelerationMode hardwareAccelerationMode;
		try
		{
			options = parser.parse(args);
			hardwareAccelerationMode = options.valueOf(mode);
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

		final boolean nodiff = options.has("nodiff");
		final boolean insecureSkipTlsVerification = options.has("insecure-skip-tls-verification");
		final boolean postInstall = options.has("postinstall");

		// Setup debug
		final boolean isDebug = options.has("debug");
		LOGS_DIR.mkdirs();

		if (isDebug)
		{
			final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
			logger.setLevel(Level.DEBUG);
		}

		// RTSS triggers off of the CreateWindow event, so this needs to be in place early, prior to splash screen
		initDllBlacklist();

		try
		{
			log.info("RuneLite Launcher version {}", LauncherProperties.getVersion());

			final List<String> jvmProps = new ArrayList<>();
			if (options.has("scale"))
			{
				// On Vista+ this calls SetProcessDPIAware(). Since the RuneLite.exe manifest is DPI unaware
				// Windows will scale the application if this isn't called. Thus the default scaling mode is
				// Windows scaling due to being DPI unaware.
				// https://docs.microsoft.com/en-us/windows/win32/hidpi/high-dpi-desktop-application-development-on-windows
				jvmProps.add("-Dsun.java2d.dpiaware=true");
				// This sets the Java 2D scaling factor, overriding the default behavior of detecting the scale via
				// GetDpiForMonitor.
				jvmProps.add("-Dsun.java2d.uiScale=" + options.valueOf("scale"));
			}

			log.info("Setting hardware acceleration to {}", hardwareAccelerationMode);
			jvmProps.addAll(hardwareAccelerationMode.toParams(OS.getOs()));

			// Always use IPv4 over IPv6
			jvmProps.add("-Djava.net.preferIPv4Stack=true");
			jvmProps.add("-Djava.net.preferIPv4Addresses=true");

			// As of JDK-8243269 (11.0.8) and JDK-8235363 (14), AWT makes macOS dark mode support opt-in so interfaces
			// with hardcoded foreground/background colours don't get broken by system settings. Considering the native
			// Aqua we draw consists a window border and an about box, it's safe to say we can opt in.
			if (OS.getOs() == OS.OSType.MacOS)
			{
				jvmProps.add("-Dapple.awt.application.appearance=system");
			}

			// Stream launcher version
			jvmProps.add("-D" + LauncherProperties.getVersionKey() + "=" + LauncherProperties.getVersion());

			if (insecureSkipTlsVerification)
			{
				jvmProps.add("-Drunelite.insecure-skip-tls-verification=true");
			}

			// java2d properties have to be set prior to the graphics environment startup
			setJvmParams(jvmProps);

			List<String> jvmParams = new ArrayList<>();
			// Set hs_err_pid location. This is a jvm param and can't be set at runtime.
			log.debug("Setting JVM crash log location to {}", CRASH_FILES);
			jvmParams.add("-XX:ErrorFile=" + CRASH_FILES.getAbsolutePath());

			if (insecureSkipTlsVerification)
			{
				setupInsecureTrustManager();
			}

			if (postInstall)
			{
				postInstall(jvmParams);
				return;
			}

			SplashScreen.init();
			SplashScreen.stage(0, "Preparing", "Setting up environment");

			// Print out system info
			if (log.isDebugEnabled())
			{
				log.debug("Command line arguments: {}", String.join(" ", args));
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

			SplashScreen.stage(.05, null, "Downloading bootstrap");
			Bootstrap bootstrap;
			try
			{
				bootstrap = getBootstrap();
			}
			catch (IOException | VerificationException | CertificateException | SignatureException | InvalidKeyException | NoSuchAlgorithmException ex)
			{
				log.error("error fetching bootstrap", ex);
				SwingUtilities.invokeLater(() -> FatalErrorDialog.showNetErrorWindow("downloading the bootstrap", ex));
				return;
			}

			SplashScreen.stage(.10, null, "Tidying the cache");

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

			boolean nojvm = "true".equals(System.getProperty("runelite.launcher.nojvm"));

			if (launcherTooOld || (nojvm && jvmTooOld))
			{
				SwingUtilities.invokeLater(() ->
					new FatalErrorDialog("Your launcher is to old to start RuneLite. Please download and install a more " +
						"recent one from RuneLite.net.")
						.addButton("RuneLite.net", () -> LinkBrowser.browse(LauncherProperties.getDownloadLink()))
						.open());
				return;
			}
			if (jvmTooOld)
			{
				SwingUtilities.invokeLater(() ->
					new FatalErrorDialog("Your Java installation is too old. RuneLite now requires Java " +
						bootstrap.getRequiredJVMVersion() + " to run. You can get a platform specific version from RuneLite.net," +
						" or install a newer version of Java.")
						.addButton("RuneLite.net", () -> LinkBrowser.browse(LauncherProperties.getDownloadLink()))
						.open());
				return;
			}

			// update packr vmargs. The only extra vmargs we need to write to disk are the ones which cannot be set
			// at runtime, which currently is just the vm errorfile.
			PackrConfig.updateLauncherArgs(bootstrap, jvmParams);

			REPO_DIR.mkdirs();

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
				download(artifacts, nodiff);
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

			final Collection<String> clientArgs = getClientArgs(options);

			if (isDebug)
			{
				clientArgs.add("--debug");
			}

			SplashScreen.stage(.90, "Starting the client", "");

			List<File> classpath = artifacts.stream()
				.map(dep -> new File(REPO_DIR, dep.getName()))
				.collect(Collectors.toList());

			// packr doesn't let us specify command line arguments
			if (nojvm || options.has("nojvm"))
			{
				try
				{
					ReflectionLauncher.launch(classpath, clientArgs);
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
					JvmLauncher.launch(bootstrap, classpath, clientArgs, jvmProps, jvmParams);
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

	private static void setJvmParams(final Collection<String> params)
	{
		for (String param : params)
		{
			final String[] split = param.replace("-D", "").split("=");
			System.setProperty(split[0], split[1]);
		}
	}

	private static Bootstrap getBootstrap() throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, VerificationException
	{
		URL u = new URL(LauncherProperties.getBootstrap());
		URL signatureUrl = new URL(LauncherProperties.getBootstrapSig());

		URLConnection conn = u.openConnection();
		URLConnection signatureConn = signatureUrl.openConnection();

		conn.setRequestProperty("User-Agent", USER_AGENT);
		signatureConn.setRequestProperty("User-Agent", USER_AGENT);

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

	private static Collection<String> getClientArgs(OptionSet options)
	{
		final Collection<String> args = options.nonOptionArguments().stream()
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.collect(Collectors.toCollection(ArrayList::new));

		String clientArgs = System.getenv("RUNELITE_ARGS");
		if (!Strings.isNullOrEmpty(clientArgs))
		{
			args.addAll(Splitter.on(' ').omitEmptyStrings().trimResults().splitToList(clientArgs));
		}

		clientArgs = (String) options.valueOf("clientargs");
		if (!Strings.isNullOrEmpty(clientArgs))
		{
			args.addAll(Splitter.on(' ').omitEmptyStrings().trimResults().splitToList(clientArgs));
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
					catch (FileNotFoundException ex)
					{
						oldhash = null;
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
						HashingOutputStream fout = new HashingOutputStream(Hashing.sha256(), new FileOutputStream(dest)))
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

			try (FileOutputStream fout = new FileOutputStream(dest))
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
		return Files.asByteSource(file).hash(sha256).toString();
	}

	private static Certificate getCertificate() throws CertificateException
	{
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		Certificate certificate = certFactory.generateCertificate(Launcher.class.getResourceAsStream("runelite.crt"));
		return certificate;
	}

	@VisibleForTesting
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

	private static void download(String path, String hash, IntConsumer progress, OutputStream out) throws IOException, VerificationException
	{
		URL url = new URL(path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestProperty("User-Agent", USER_AGENT);
		conn.getResponseCode();

		InputStream err = conn.getErrorStream();
		if (err != null)
		{
			err.close();
			throw new IOException("Unable to download " + path + " - " + conn.getResponseMessage());
		}

		int downloaded = 0;
		HashingOutputStream hout = new HashingOutputStream(Hashing.sha256(), out);
		try (InputStream in = conn.getInputStream())
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

	private static void setupInsecureTrustManager() throws NoSuchAlgorithmException, KeyManagementException
	{
		TrustManager trustManager = new X509TrustManager()
		{
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType)
			{
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType)
			{
			}

			@Override
			public X509Certificate[] getAcceptedIssuers()
			{
				return null;
			}
		};

		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, new TrustManager[]{trustManager}, new SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
	}

	private static void postInstall(List<String> jvmParams)
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

		PackrConfig.updateLauncherArgs(bootstrap, jvmParams);

		log.info("Performed postinstall steps");
	}

	private static void initDllBlacklist()
	{
		if (OS.getOs() != OS.OSType.Windows)
		{
			return;
		}

		String blacklistedDlls = System.getProperty("runelite.launcher.blacklistedDlls");
		if (blacklistedDlls == null || blacklistedDlls.isEmpty())
		{
			return;
		}

		String arch = System.getProperty("os.arch");
		if (!"x86".equals(arch) && !"amd64".equals(arch))
		{
			log.debug("System architecture is not supported for launcher natives: {}", arch);
			return;
		}

		String[] dlls = blacklistedDlls.split(",");

		try
		{
			System.loadLibrary("launcher_" + arch);
			log.debug("Setting blacklisted dlls: {}", blacklistedDlls);
			setBlacklistedDlls(dlls);
		}
		catch (Error ex)
		{
			log.debug("Error setting dll blacklist", ex);
		}
	}

	private static native void setBlacklistedDlls(String[] dlls);
}

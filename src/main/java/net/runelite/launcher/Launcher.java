package net.runelite.launcher;

import com.jcabi.aether.Aether;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import net.runelite.launcher.ui.LauncherUI;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;

public class Launcher
{
	private static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	private static final File LIB_DIR = new File(RUNELITE_DIR, "libs");

//	private List<Artifact> resolveArtifacts(Artifact artifact) throws DependencyResolutionException
//	{
//		RemoteRepository remoteRepository = new RemoteRepository("runelite", null, "http://192.168.1.2/rs/repo");
//
//		Aether a = new Aether(Arrays.asList(remoteRepository), LIB_DIR);
//
//		Collection<Artifact> deps = a.resolve(artifact, "runtime");
//		return new ArrayList<>(deps);
//	}
//
	public static void main(String[] args) throws Exception
	{
		new LauncherUI();

		ArtifactResolver resolver = new ArtifactResolver(LIB_DIR, new RemoteRepository("runelite", null, "http://192.168.1.2/rs/repo"));
		Artifact a = new DefaultArtifact("net.runelite.rs", "client", "", "jar", "0.0.0");

		resolver.resolveArtifacts(a);

//		File rlDir = Util.getRuneliteDir();
//		File runeliteFile = new File(rlDir, "runelite.jar");
//
//		Util.download(new URL("http://bootstrap.runelite.net"), runeliteFile);
//
//		JarFile runeliteJarfile = new JarFile(runeliteFile);
//
//		verify(runeliteJarfile, getCertificate());
//
//		URLClassLoader loader = new URLClassLoader(new URL[] { runeliteFile.toURI().toURL() });
//		Launchable l = (Launchable) loader.loadClass("net.runelite.client.Launcher").newInstance();
//		l.run();
	}
}

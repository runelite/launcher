package net.runelite.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Launcher
{
	private static Certificate getCertificate() throws CertificateException
	{
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		return certFactory.generateCertificate(Launcher.class.getResourceAsStream("/runelite.crt"));
	}
	
	public static void main(String[] args) throws Exception
	{
		File rlDir = Util.getRuneliteDir();
		File runeliteFile = new File(rlDir, "runelite.jar");
		
		Util.download(new URL("http://bootstrap.runelite.net"), runeliteFile);
		
		JarFile runeliteJarfile = new JarFile(runeliteFile);
		
		verify(runeliteJarfile, getCertificate());
		
		URLClassLoader loader = new URLClassLoader(new URL[] { runeliteFile.toURI().toURL() });
		Launchable l = (Launchable) loader.loadClass("net.runelite.client.Launcher").newInstance();
		l.run();
	}
	
	private static void verify(JarFile jarFile, Certificate certificate) throws IOException
	{
		List<JarEntry> jarEntries = new ArrayList<>();

		// Ensure the jar file is signed.
		Manifest man = jarFile.getManifest();
		if (man == null)
		{
			throw new SecurityException("The provider is not signed");
		}

		// Ensure all the entries' signatures verify correctly
		byte[] buffer = new byte[8192];
		Enumeration entries = jarFile.entries();

		while (entries.hasMoreElements())
		{
			JarEntry je = (JarEntry) entries.nextElement();

			// Skip directories.
			if (je.isDirectory() || je.getName().startsWith("META-INF/"))
			{
				continue;
			}

			// Read in each jar entry. A security exception will
			// be thrown if a signature/digest check fails.
			try (InputStream is = jarFile.getInputStream(je))
			{
				// Read in each jar entry. A security exception will
				// be thrown if a signature/digest check fails.
				while (is.read(buffer, 0, buffer.length) != -1);
			}

			jarEntries.add(je);
		}

		// Get the list of signer certificates
		for (JarEntry je : jarEntries)
		{
			// Every file must be signed except files in META-INF.
			Certificate[] certs = je.getCertificates();
			if (certs == null || certs.length == 0)
			{
				throw new SecurityException("The provider has unsigned class files. " + je);
			}

			Certificate cert = certs[0];

			if (!certificate.equals(cert))
				throw new SecurityException("The provider is not signed by a trusted signer");
		}
	}
}

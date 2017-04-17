/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
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

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JarVerifier
{
	/**
	 * Verify the jar is signed by the given certificate
	 * @param jarFile
	 * @param certificate
	 * @throws IOException 
	 */
	public static void verify(JarFile jarFile, Certificate certificate) throws IOException
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
				throw new SecurityException("The jar contains an unsigned file: " + je);
			}

			Certificate cert = certs[0];

			if (!certificate.equals(cert))
			{
				throw new SecurityException("The jar is not signed by a trusted signer");
			}
		}
	}
}

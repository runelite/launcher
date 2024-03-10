/*
 * Copyright (c) 2023, Adam <Adam@sigterm.info>
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

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

class TrustManagerUtil
{
	private static TrustManager[] loadTrustManagers(String trustStoreType) throws KeyStoreException, NoSuchAlgorithmException
	{
		// javax.net.ssl.trustStoreType controls which keystore implementation the TrustStoreManager uses
		String old;
		if (trustStoreType != null)
		{
			old = System.setProperty("javax.net.ssl.trustStoreType", trustStoreType);
		}
		else
		{
			old = System.clearProperty("javax.net.ssl.trustStoreType");
		}

		var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init((KeyStore) null);

		var trustManagers = trustManagerFactory.getTrustManagers();

		// restore old value
		if (old == null)
		{
			System.clearProperty("javax.net.ssl.trustStoreType");
		}
		else
		{
			System.setProperty("javax.net.ssl.trustStoreType", old);
		}

		return trustManagers;
	}

	static void setupTrustManager() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException
	{
		if (OS.getOs() != OS.OSType.Windows)
		{
			return;
		}

		// Use the Windows Trusted Root Certificate Authorities in addition to the bundled cacerts.
		// Corporations, schools, antivirus, and malware commonly install root certificates onto
		// machines for security or other reasons that are not present in the JRE certificate store.
		var jreTms = loadTrustManagers(null);
		var windowsTms = loadTrustManagers("Windows-ROOT");

		var trustManagers = new TrustManager[jreTms.length + windowsTms.length];
		System.arraycopy(jreTms, 0, trustManagers, 0, jreTms.length);
		System.arraycopy(windowsTms, 0, trustManagers, jreTms.length, windowsTms.length);

		// Even though SSLContext.init() accepts TrustManager[], Sun's SSLContextImpl only picks the first
		// X509TrustManager and uses that.
		var combiningTrustManager = new X509TrustManager()
		{
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
			{
				CertificateException exception = null;
				for (var trustManager : trustManagers)
				{
					if (trustManager instanceof X509TrustManager)
					{
						try
						{
							((X509TrustManager) trustManager).checkClientTrusted(chain, authType);
							// accept if any of the trust managers accept the certificate
							return;
						}
						catch (CertificateException ex)
						{
							exception = ex;
						}
					}
				}

				if (exception != null)
				{
					throw exception;
				}

				throw new CertificateException("no X509TrustManagers present");
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
			{
				CertificateException exception = null;
				for (var trustManager : trustManagers)
				{
					if (trustManager instanceof X509TrustManager)
					{
						try
						{
							((X509TrustManager) trustManager).checkServerTrusted(chain, authType);
							// accept if any of the trust managers accept the certificate
							return;
						}
						catch (CertificateException ex)
						{
							exception = ex;
						}
					}
				}

				if (exception != null)
				{
					throw exception;
				}

				throw new CertificateException("no X509TrustManagers present");
			}

			@Override
			public X509Certificate[] getAcceptedIssuers()
			{
				var certificates = new ArrayList<X509Certificate>();
				for (var trustManager : trustManagers)
				{
					if (trustManager instanceof X509TrustManager)
					{
						certificates.addAll(Arrays.asList(((X509TrustManager) trustManager).getAcceptedIssuers()));
					}
				}
				return certificates.toArray(new X509Certificate[0]);
			}
		};

		SSLContext sc = SSLContext.getInstance("TLS");
		sc.init(null, new TrustManager[]{combiningTrustManager}, new SecureRandom());
		SSLContext.setDefault(sc);
	}

	static void setupInsecureTrustManager() throws NoSuchAlgorithmException, KeyManagementException
	{
		// the insecure trust manager trusts everything
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
				return new X509Certificate[0];
			}
		};

		SSLContext sc = SSLContext.getInstance("TLS");
		sc.init(null, new TrustManager[]{trustManager}, new SecureRandom());
		SSLContext.setDefault(sc);
	}
}

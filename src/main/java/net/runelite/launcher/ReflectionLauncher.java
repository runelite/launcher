/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import javax.swing.UIManager;
import joptsimple.OptionSet;
import static net.runelite.launcher.Launcher.CLIENT_MAIN_CLASS;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ReflectionLauncher
{
	private static final Logger logger = LoggerFactory.getLogger(ReflectionLauncher.class);

	public static void launch(List<ArtifactResult> results, String clientArgs, OptionSet options) throws Exception
	{
		URL[] jarUrls = new URL[results.size()];
		int i = 0;
		for (ArtifactResult ar : results)
		{
			URL url = ar.getArtifact().getFile().toURI().toURL();
			logger.debug("Adding jar: {}", url);
			jarUrls[i++] = url;
		}

		URLClassLoader loader = new URLClassLoader(jarUrls, null);

		UIManager.put("ClassLoader", loader); // hack for Substance
		Thread thread = new Thread()
		{
			public void run()
			{
				try
				{
					Class<?> mainClass = loader.loadClass(CLIENT_MAIN_CLASS);

					Method main = mainClass.getMethod("main", String[].class);

					String[] args = clientArgs != null ? clientArgs.split(" ") : new String[0];
					main.invoke(null, (Object) args);
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}
			}
		};
		thread.setName("RuneLite");
		thread.start();
	}
}

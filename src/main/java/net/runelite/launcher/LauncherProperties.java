/*
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *	list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *	this list of conditions and the following disclaimer in the documentation
 *	and/or other materials provided with the distribution.
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
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LauncherProperties
{
	private static final String LAUNCHER_VERSION = "runelite.launcher.version";
	private static final String DISCORD_INVITE = "runelite.discord.invite";
	private static final String TROUBLESHOOTING_LINK = "runelite.wiki.troubleshooting.link";
	private static final String DNS_CHANGE_LINK = "runelite.dnschange.link";
	private static final String DOWNLOAD_LINK = "runelite.download.link";
	private static final String BOOTSTRAP = "runelite.bootstrap";
	private static final String BOOTSTRAPSIG = "runelite.bootstrapsig";
	private static final String MAIN = "runelite.main";
	private static final String RUNELITE_128 = "runelite.128";
	private static final String RUNELITE_SPLASH = "runelite.splash";

	private static final Properties properties = new Properties();

	static
	{
		final InputStream in = LauncherProperties.class.getResourceAsStream("launcher.properties");

		try
		{
			properties.load(in);
		}
		catch (IOException ex)
		{
			log.warn("Unable to load properties", ex);
		}
	}

	public static String getVersionKey()
	{
		return LAUNCHER_VERSION;
	}

	public static String getVersion()
	{
		return properties.getProperty(LAUNCHER_VERSION);
	}

	public static String getDiscordInvite()
	{
		return properties.getProperty(DISCORD_INVITE);
	}

	public static String getTroubleshootingLink()
	{
		return properties.getProperty(TROUBLESHOOTING_LINK);
	}

	public static String getDNSChangeLink()
	{
		return properties.getProperty(DNS_CHANGE_LINK);
	}

	public static String getDownloadLink()
	{
		return properties.getProperty(DOWNLOAD_LINK);
	}

	public static String getBootstrap()
	{
		return properties.getProperty(BOOTSTRAP);
	}

	public static String getBootstrapSig()
	{
		return properties.getProperty(BOOTSTRAPSIG);
	}

	public static String getMain()
	{
		return properties.getProperty(MAIN);
	}

	public static String getRuneLite128()
	{
		return properties.getProperty(RUNELITE_128);
	}

	public static String getRuneLiteSplash()
	{
		return properties.getProperty(RUNELITE_SPLASH);
	}
}

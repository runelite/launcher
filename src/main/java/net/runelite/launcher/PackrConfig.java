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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Bootstrap;

@Slf4j
class PackrConfig
{
	// Update the packr config
	static void updateLauncherArgs(Bootstrap bootstrap)
	{
		var os = OS.getOs();
		if (os != OS.OSType.Windows && os != OS.OSType.MacOS)
		{
			return;
		}

		File configFile = new File("config.json").getAbsoluteFile();
		if (!configFile.exists() || !configFile.canWrite())
		{
			return;
		}

		Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.create();
		Map config;
		try (FileInputStream fin = new FileInputStream(configFile))
		{
			config = gson.fromJson(new InputStreamReader(fin), Map.class);
		}
		catch (IOException | JsonIOException | JsonSyntaxException e)
		{
			log.warn("error deserializing packr vm args!", e);
			return;
		}

		if (config == null)
		{
			// this can't happen when run from the launcher, because an invalid packr config would prevent the launcher itself
			// from starting. But could happen if the jar launcher was run separately.
			log.warn("packr config is null!");
			return;
		}

		String[] argsArr = getArgs(bootstrap);
		if (argsArr == null || argsArr.length == 0)
		{
			log.warn("Launcher args are empty");
			return;
		}

		config.put("vmArgs", argsArr);
		config.put("env", getEnv(bootstrap));

		try
		{
			File tmpFile = File.createTempFile("runelite", null);

			try (FileOutputStream fout = new FileOutputStream(tmpFile);
				FileChannel channel = fout.getChannel();
				PrintWriter writer = new PrintWriter(fout))
			{
				channel.lock();
				writer.write(gson.toJson(config));
				channel.force(true);
				// FileChannel.close() frees the lock
			}

			try
			{
				Files.move(tmpFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException ex)
			{
				log.debug("atomic move not supported", ex);
				Files.move(tmpFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			log.warn("error updating packr vm args!", e);
		}
	}

	private static String[] getArgs(Bootstrap bootstrap)
	{
		return Launcher.isJava17() ? getArgsJvm17(bootstrap) : getArgsJvm11(bootstrap);
	}

	private static String[] getArgsJvm17(Bootstrap bootstrap)
	{
		switch (OS.getOs())
		{
			case Windows:
				String[] args = bootstrap.getLauncherJvm17WindowsArguments();
				return args != null ? args : bootstrap.getLauncherJvm17Arguments();
			case MacOS:
				args = bootstrap.getLauncherJvm17MacArguments();
				return args != null ? args : bootstrap.getLauncherJvm17Arguments();
			default:
				return bootstrap.getLauncherJvm17Arguments();
		}
	}

	private static String[] getArgsJvm11(Bootstrap bootstrap)
	{
		switch (OS.getOs())
		{
			case Windows:
				String[] args = bootstrap.getLauncherJvm11WindowsArguments();
				return args != null ? args : bootstrap.getLauncherJvm11Arguments();
			case MacOS:
				args = bootstrap.getLauncherJvm11MacArguments();
				return args != null ? args : bootstrap.getLauncherJvm11Arguments();
			default:
				return bootstrap.getLauncherJvm11Arguments();
		}
	}

	private static Map<String, String> getEnv(Bootstrap bootstrap)
	{
		switch (OS.getOs())
		{
			case Windows:
				return bootstrap.getLauncherWindowsEnv();
			case MacOS:
				return bootstrap.getLauncherMacEnv();
			case Linux:
				return bootstrap.getLauncherLinuxEnv();
			default:
				return null;
		}
	}
}

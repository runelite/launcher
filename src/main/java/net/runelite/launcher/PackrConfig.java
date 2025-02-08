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
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Bootstrap;

@Slf4j
class PackrConfig
{
	// Update the packr config
	static void updateLauncherArgs(Bootstrap bootstrap, LauncherSettings settings)
	{
		String[] bootstrapVmArgs = getVmArgs(bootstrap);
		if (bootstrapVmArgs == null || bootstrapVmArgs.length == 0)
		{
			log.warn("Launcher args are empty");
			return;
		}

		List<String> vmArgs = new ArrayList<>(Arrays.asList(bootstrapVmArgs));

		// java.net.preferIPv4Stack needs to be set prior to libnet *loading* (it is read in net_util.c JNI_OnLoad).
		// Failure to keep preferIPv4Stack consistent between libnet and java/net results in disagreements over
		// which socket types can be used.
		if (settings.ipv4)
		{
			vmArgs.add("-Djava.net.preferIPv4Stack=true");
		}

		Map<String, String> env = getEnv(bootstrap);

		patch(config ->
		{
			config.put("vmArgs", vmArgs);
			config.put("env", env);
		});
	}

	static void patch(Consumer<Map> configConsumer)
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
			log.warn("error deserializing launcher vm args!", e);
			return;
		}

		if (config == null)
		{
			// this can't happen when run from the launcher, because an invalid packr config would prevent the launcher itself
			// from starting. But could happen if the jar launcher was run separately.
			log.warn("launcher config is null!");
			return;
		}

		configConsumer.accept(config);

		try
		{
			File tmpFile = File.createTempFile("runelite", null);

			try (FileOutputStream fout = new FileOutputStream(tmpFile);
				FileChannel channel = fout.getChannel();
				OutputStreamWriter writer = new OutputStreamWriter(fout, StandardCharsets.UTF_8))
			{
				channel.lock();
				gson.toJson(config, writer);
				writer.flush();
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

			log.debug("patched packr config");
		}
		catch (IOException e)
		{
			log.warn("error updating launcher vm args!", e);
		}
	}

	private static String[] getVmArgs(Bootstrap bootstrap)
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

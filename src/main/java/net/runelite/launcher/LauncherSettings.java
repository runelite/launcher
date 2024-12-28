/*
 * Copyright (c) 2022, Adam <Adam@sigterm.info>
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

import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import joptsimple.OptionSet;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;

@Data
@Slf4j
class LauncherSettings
{
	private static final String LAUNCHER_SETTINGS = "settings.json";

	long lastUpdateAttemptTime;
	String lastUpdateHash;
	int lastUpdateAttemptNum;

	// configuration
	boolean debug;
	boolean nodiffs;
	boolean skipTlsVerification;
	boolean noupdates;
	boolean safemode;
	boolean ipv4;
	@Nullable
	Double scale;
	List<String> clientArguments = Collections.emptyList();
	List<String> jvmArguments = Collections.emptyList();
	HardwareAccelerationMode hardwareAccelerationMode = HardwareAccelerationMode.AUTO;
	LaunchMode launchMode = LaunchMode.AUTO;

	// override settings with options from cli
	void apply(OptionSet options)
	{
		if (options.has("debug"))
		{
			debug = true;
		}
		if (options.has("nodiff"))
		{
			nodiffs = true;
		}
		if (options.has("insecure-skip-tls-verification"))
		{
			skipTlsVerification = true;
		}
		if (options.has("noupdate"))
		{
			noupdates = true;
		}
		if (options.has("scale"))
		{
			scale = Double.parseDouble(String.valueOf(options.valueOf("scale")));
		}

		if (options.has("J"))
		{
			jvmArguments = options.valuesOf("J").stream()
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.collect(Collectors.toList());
		}

		if (!options.nonOptionArguments().isEmpty()) // client arguments
		{
			clientArguments = options.nonOptionArguments().stream()
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.collect(Collectors.toList());
		}

		if (options.has("hw-accel"))
		{
			hardwareAccelerationMode = (HardwareAccelerationMode) options.valueOf("hw-accel");
		}
		else if (options.has("mode"))
		{
			hardwareAccelerationMode = (HardwareAccelerationMode) options.valueOf("mode");
		}

		// we use runelite.launcher.reflect to signal to use the reflect launch mode from the debug plugin
		if ("true".equals(System.getProperty("runelite.launcher.reflect")))
		{
			launchMode = LaunchMode.REFLECT;
		}
		else if (options.has("launch-mode"))
		{
			launchMode = (LaunchMode) options.valueOf("launch-mode");
		}
	}

	String configurationStr()
	{
		return MessageFormatter.arrayFormat(
				" debug: {}" + System.lineSeparator() +
				" nodiffs: {}" + System.lineSeparator() +
				" skip tls verification: {}" + System.lineSeparator() +
				" noupdates: {}" + System.lineSeparator() +
				" safe mode: {}" + System.lineSeparator() +
				" ipv4: {}" + System.lineSeparator() +
				" scale: {}" + System.lineSeparator() +
				" client arguments: {}" + System.lineSeparator() +
				" jvm arguments: {}" + System.lineSeparator() +
				" hardware acceleration mode: {}" + System.lineSeparator() +
				" launch mode: {}",
			new Object[]{
				debug,
				nodiffs,
				skipTlsVerification,
				noupdates,
				safemode,
				ipv4,
				scale == null ? "system" : scale,
				clientArguments.isEmpty() ? "none" : clientArguments,
				jvmArguments.isEmpty() ? "none" : jvmArguments,
				hardwareAccelerationMode,
				launchMode
			}
		).getMessage();
	}

	@Nonnull
	static LauncherSettings loadSettings()
	{
		var settingsFile = new File(LAUNCHER_SETTINGS).getAbsoluteFile();
		try (var in = new InputStreamReader(new FileInputStream(settingsFile), StandardCharsets.UTF_8))
		{
			var settings = new Gson()
				.fromJson(in, LauncherSettings.class);
			return MoreObjects.firstNonNull(settings, new LauncherSettings());
		}
		catch (FileNotFoundException ex)
		{
			log.debug("unable to load settings, file does not exist");
			return new LauncherSettings();
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("unable to load settings", e);
			return new LauncherSettings();
		}
	}

	static void saveSettings(LauncherSettings settings)
	{
		var settingsFile = new File(LAUNCHER_SETTINGS).getAbsoluteFile();

		try
		{
			File tmpFile = File.createTempFile(LAUNCHER_SETTINGS, "json");
			Gson gson = new GsonBuilder()
				.setPrettyPrinting()
				.create();

			try (FileOutputStream fout = new FileOutputStream(tmpFile);
				FileChannel channel = fout.getChannel();
				OutputStreamWriter writer = new OutputStreamWriter(fout, StandardCharsets.UTF_8))
			{
				channel.lock();
				gson.toJson(settings, writer);
				writer.flush();
				channel.force(true);
				// FileChannel.close() frees the lock
			}

			try
			{
				Files.move(tmpFile.toPath(), settingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException ex)
			{
				log.debug("atomic move not supported", ex);
				Files.move(tmpFile.toPath(), settingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			log.error("unable to save launcher settings!", e);
			settingsFile.delete();
		}
	}
}

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
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
class LauncherSettings
{
	private static final String LAUNCHER_SETTINGS = "settings.json";

	long lastUpdateAttemptTime;
	String lastUpdateHash;
	int lastUpdateAttemptNum;

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
			var gson = new Gson();

			try (FileOutputStream fout = new FileOutputStream(tmpFile);
				FileChannel channel = fout.getChannel();
				OutputStreamWriter writer = new OutputStreamWriter(fout, StandardCharsets.UTF_8))
			{
				channel.lock();
				writer.write(gson.toJson(settings));
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

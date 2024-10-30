/*
 * Copyright (c) 2024, Adam <Adam@sigterm.info>
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

import com.google.common.base.Stopwatch;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import static net.runelite.launcher.Launcher.LAUNCHER_EXECUTABLE_NAME_WIN;
import static net.runelite.launcher.Launcher.RUNELITE_DIR;
import static net.runelite.launcher.Launcher.isProcessElevated;
import static net.runelite.launcher.Launcher.nativesLoaded;
import static net.runelite.launcher.Launcher.setFileACL;

@Slf4j
class FilesystemPermissions
{
	// https://learn.microsoft.com/en-us/windows/win32/secauthz/well-known-sids
	private static final String SID_SYSTEM = "S-1-5-18";
	private static final String SID_ADMINISTRATORS = "S-1-5-32-544";
	private static final int MAX_FILES_PER_DIRECTORY = 64;

	static boolean check()
	{
		if (!nativesLoaded)
		{
			log.debug("Launcher natives were not loaded. Skipping filesystem permission check.");
			return false;
		}

		final boolean elevated = isProcessElevated(ProcessHandle.current().pid());
		// It is possible for .runelite to exist but be not writable, even when elevated. But we can update the ACLs
		// always when elevated, so attempt to fix the ACLs first.
		if (elevated)
		{
			log.info("RuneLite is running as an administrator. This is not recommended because it can cause the files " +
					"RuneLite writes to {} to have more strict permissions than would otherwise be required.",
				RUNELITE_DIR);

			try
			{
				final var sid = Launcher.getUserSID();
				log.info("RuneLite is updating the ACLs of the files in {} to be: NT AUTHORITY\\SYSTEM, BUILTIN\\Administrators, " +
						"and {} (your user SID). To avoid this, don't run RuneLite with elevated permissions.",
					RUNELITE_DIR, sid);

				// Files.walk is depth-first, which doesn't work if the permissions on the root don't allow traversal.
				// So we do our own walk.
				Stopwatch sw = Stopwatch.createStarted();
				setTreeACL(RUNELITE_DIR, sid);
				sw.stop();
				log.debug("setTreeACL time: {}", sw);
			}
			catch (Exception ex)
			{
				log.error("Unable to update file permissions", ex);
			}
		}

		if (!RUNELITE_DIR.exists())
		{
			if (!RUNELITE_DIR.mkdirs())
			{
				log.error("unable to create directory {} elevated: {}", RUNELITE_DIR, elevated);

				String message;
				if (elevated)
				{
					message = "Unable to create RuneLite directory " + RUNELITE_DIR + " while elevated. Check your filesystem permissions are correct.";
				}
				else
				{
					message = "Unable to create RuneLite directory " + RUNELITE_DIR + ". Check your filesystem permissions are correct. If you rerun RuneLite" +
						" as an administrator, RuneLite will attempt to create the directory again and fix its permissions.";
				}
				SwingUtilities.invokeLater(() ->
				{
					var dialog = new FatalErrorDialog(message);
					if (!elevated)
					{
						dialog.addButton("Run as administrator", FilesystemPermissions::runas);
					}
					dialog.open();
				});
				return true;
			}

			if (elevated)
			{
				// Set the correct permissions on the newly created folder. This sets object inherit and container inherit,
				// so all future files in .runelite should then have the correct permissions.
				try
				{
					final var sid = Launcher.getUserSID();
					setTreeACL(RUNELITE_DIR, sid);
				}
				catch (Exception ex)
				{
					log.error("Unable to update file permissions", ex);
				}
			}
		}

		Stopwatch sw = Stopwatch.createStarted();
		boolean permissionsOk = checkPermissions(RUNELITE_DIR, true);
		sw.stop();
		log.debug("checkPermissions time: {}", sw);

		if (!permissionsOk)
		{
			String message;
			if (elevated)
			{
				// This means the previous ACL update above did not work...?
				message = "The file permissions of " + RUNELITE_DIR + ", or a file within it, is not correct. Check the logs for more details.";
			}
			else
			{
				message = "The file permissions of " + RUNELITE_DIR + ", or a file within it, is not correct. Check the logs for more details." +
					" If you rerun RuneLite as an administrator, RuneLite will attempt to fix the file permissions.";
			}
			SwingUtilities.invokeLater(() ->
			{
				var dialog = new FatalErrorDialog(message);
				if (!elevated)
				{
					dialog.addButton("Run as administrator", FilesystemPermissions::runas);
				}
				dialog.open();
			});
			return true;
		}

		return false;
	}

	private static boolean checkPermissions(File tree, boolean root)
	{
		// Directory traversal and isWritable() is very slow. On my system checking 16k files
		// takes ~13 seconds with traversal. The majority of these files tend to be screenshots
		// which are less interesting.
		//
		// Traverse only the top level directories, and limit the number of files checked,
		// to keep it speedy. The primary files which prevent the launcher and client from
		// working are all here (repository2, cache, logs, profiles2).
		File[] files = tree.listFiles();
		if (files == null)
		{
			log.error("Unable to list files in directory {} (IO error, or is not a directory)", tree);
			return false;
		}

		boolean ok = true;
		int numFiles = 0;
		for (File file : files)
		{
			if (file.isDirectory())
			{
				log.debug("Checking permissions of directory {}", file);
				if (root && !checkPermissions(file, false))
				{
					ok = false;
				}
			}
			else if (numFiles++ < MAX_FILES_PER_DIRECTORY)
			{
				Path path;
				try
				{
					path = file.toPath();
				}
				catch (InvalidPathException ex)
				{
					log.error("file is not a valid path", ex);
					continue;
				}

				log.debug("Checking permissions of {}", path);
				if (!Files.isReadable(path) || !Files.isWritable(path))
				{
					log.error("Permissions for {} are incorrect. Readable: {} writable: {}",
						file, Files.isReadable(path), Files.isWritable(path));
					ok = false;
				}
			}
		}
		return ok;
	}

	private static void setTreeACL(File tree, String sid) throws IOException
	{
		log.debug("Setting ACL on {}", tree.getAbsolutePath());
		setFileACL(tree.getAbsolutePath(), new String[]{
			SID_SYSTEM,
			SID_ADMINISTRATORS,
			sid
		});
		Files.setAttribute(tree.toPath(), "dos:readonly", false);

		for (File file : tree.listFiles())
		{
			if (file.isDirectory())
			{
				setTreeACL(file, sid);
			}
			else
			{
				log.debug("Setting ACL on {}", file.getAbsolutePath());
				setFileACL(file.getAbsolutePath(), new String[]{
					SID_SYSTEM,
					SID_ADMINISTRATORS,
					sid
				});
				Files.setAttribute(file.toPath(), "dos:readonly", false);
			}
		}
	}

	private static void runas()
	{
		log.info("Relaunching as administrator");

		ProcessHandle current = ProcessHandle.current();
		var command = current.info().command();
		if (command.isEmpty())
		{
			log.error("Running process has no command");
			System.exit(-1);
			return;
		}

		Path path = Paths.get(command.get());
		if (!path.getFileName().toString().equals(LAUNCHER_EXECUTABLE_NAME_WIN))
		{
			log.error("Running process is not the launcher: {}", path.getFileName().toString());
			System.exit(-1);
			return;
		}

		String commandPath = path.toAbsolutePath().toString();
		Launcher.runas(commandPath, "");
		System.exit(0);
	}
}

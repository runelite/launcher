/*
 * Copyright (c) 2024, YvesW <https://github.com/YvesW>
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

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;
import static net.runelite.launcher.Launcher.LOGS_DIR;
import static net.runelite.launcher.Launcher.REPO_DIR;
import static net.runelite.launcher.Launcher.RUNELITE_DIR;
import static net.runelite.launcher.Launcher.filePermsStep;
import static net.runelite.launcher.Launcher.nativesLoaded;

@Slf4j
public class FilePermissionManager
{
	private static final File PROFILES_DIR = new File(RUNELITE_DIR, "profiles2");
	private static final Color DARKER_GRAY_COLOR = new Color(30, 30, 30);

	static void verifyFixFilePerms()
	{
		verifyFixFilePerms(false);
	}

	static void verifyFixFilePerms(boolean overrideVerification)
	{
		// libs are only loaded on OSType.Windows so skip checking for OSType
		if (!nativesLoaded)
		{
			log.debug("Launcher natives were not loaded. Skipping filesystem permission verification.");
			return;
		}

		log.info("Launcher is running with elevated permissions: " + isRunningElevated());
		log.info("Launcher is Jagex Launcher child: " + isJagexLauncherChild());
		if (overrideVerification)
		{
			log.info("Exception caught. Overriding filesystem permission verification to engage fixFilePerms().");
		}

		switch (filePermsStep)
		{
			case REGULAR_VERIFICATION:
			case REGULAR_VERIFICATION_JAGEX_LAUNCHER:
				regularVerification(overrideVerification);
				break;
			case FIX_USER:
			case FIX_USER_JAGEX_LAUNCHER:
				fixFilePerms(false);
				break;
			case VERIFY_POST_USER:
			case VERIFY_POST_USER_JAGEX_LAUNCHER:
				postUserFixVerification();
				break;
			case FIX_EVERYONE:
			case FIX_EVERYONE_JAGEX_LAUNCHER:
				fixFilePerms(true);
				break;
			case VERIFY_POST_EVERYONE:
			case VERIFY_POST_EVERYONE_JAGEX_LAUNCHER:
				postEveryoneFixVerification();
				break;
		}
	}

	private static boolean isRunningElevated()
	{
		return isRunningElevated(ProcessHandle.current().pid());
	}

	private static native boolean isRunningElevated(long pid);

	private static boolean isJagexLauncherChild()
	{
		// alternatively get the children or descendants of JagexLauncher.exe
		ProcessHandle parent = ProcessHandle.current().parent().orElse(null);
		if (parent != null)
		{
			return parent.info().command().orElse("-").contains("JagexLauncher.exe");
		}
		return false;
	}

	private static void regularVerification(boolean overrideVerification)
	{
		if (failedFilePermsVerification() || overrideVerification)
		{
			if (isJagexLauncherChild())
			{
				filePermsStep = FilePermsStep.REGULAR_VERIFICATION_JAGEX_LAUNCHER;
			}
			log.warn("Filesystem permissions verification failed. Requesting elevated permissions to correct permissions.");
			requestElevation("Would you like to close all RuneLite instances and restart with elevated permissions to correct these issues?");
		}
		else
		{
			log.info("Filesystem permissions verification has succeeded.");
		}
	}

	private static boolean failedFilePermsVerification()
	{
		return comboFailedFilePermsVerification(RUNELITE_DIR)
			|| comboFailedFilePermsVerification(LOGS_DIR)
			|| comboFailedFilePermsVerification(REPO_DIR)
			|| comboFailedFilePermsVerification(PROFILES_DIR);
	}

	private static boolean comboFailedFilePermsVerification(File dir)
	{
		return dirFailedFilePermsVerification(dir) || fileFailedFilePermsVerification(dir);
	}

	private static boolean dirFailedFilePermsVerification(File dir)
	{
		if (!dir.exists() && !dir.mkdirs())
		{
			log.warn("Filesystem permissions verification: unable to create directory " + dir.getAbsolutePath());
		}
		Path path = dir.toPath();
		// File::canRead/canWrite/canExecute only check the file attributes, not the ACL (the error fixed by icacls).
		// Files::isReadable/isWritable/isExecutable do properly check the file attributes and the ACL.
		if (!Files.isReadable(path) || !Files.isWritable(path) || !Files.isExecutable(path))
		{
			log.warn("Filesystem permissions verification: verification failed for directory " + dir.getAbsolutePath());
			return true;
		}
		return false;
	}

	private static boolean fileFailedFilePermsVerification(File dir)
	{
		boolean result = false;
		File testFile = new File(dir, "FilePermsVerification");
		Path path = testFile.toPath();
		try
		{
			Files.createFile(path);
		}
		catch (IOException ex)
		{
			log.error("Filesystem permissions verification: unable to create test file " + testFile.getAbsolutePath(), ex);
			// Files::isWritable also returns false if the file does not exist; no need to return here
		}
		// File::canRead/canWrite/canExecute only check the file attributes, not the ACL (the error fixed by icacls).
		// Files::isReadable/isWritable/isExecutable do properly check the file attributes and the ACL.
		if (!Files.isReadable(path) || !Files.isWritable(path) || !Files.isExecutable(path))
		{
			log.warn("Filesystem permissions verification: verification failed for file " + testFile.getAbsolutePath());
			result = true;
		}
		try
		{
			Files.deleteIfExists(path);
		}
		catch (IOException ignored)
		{
			// it is not problematic if the file is not deleted
		}
		return result;
	}

	private static void requestElevation(String messageAddition)
	{
		try (var in = FilePermissionManager.class.getResourceAsStream("runelite_128.png"))
		{
			assert in != null;
			ImageIcon icon = new ImageIcon(ImageIO.read(in));
			icon = new ImageIcon(icon.getImage().getScaledInstance(64, 64, java.awt.Image.SCALE_SMOOTH));
			UIManager.put("OptionPane.background", DARKER_GRAY_COLOR);
			UIManager.put("Panel.background", DARKER_GRAY_COLOR);
			UIManager.put("OptionPane.messageForeground", Color.WHITE);
			UIManager.put("OptionPane.sameSizeButtons", true);
			UIManager.put("OptionPane.okButtonText", "Restart");
			String title = "RuneLite - Filesystem permission problems detected";
			String message = "RuneLite has detected filesystem permission problems.\n" + messageAddition;

			final int result = JOptionPane.showConfirmDialog(null,
				message,
				title,
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE,
				icon);

			if (result == JOptionPane.OK_OPTION)
			{
				terminateProcesses();
				elevateLauncher(fixArgs());
			}
		}
		catch (Exception ex)
		{
			log.error("Elevation request: error showing JOptionPane.", ex);
			terminateProcesses();
			elevateLauncher(fixArgs());
		}
	}

	private static void terminateProcesses()
	{
		// terminate all other RuneLite.exe processes
		String path = ProcessHandle.current().info().command().orElse(null);

		if (path == null)
		{
			log.debug("Aborting termination of other RuneLite processes. Launcher path is null.");
			return;
		}

		long pidCurrent = ProcessHandle.current().pid();
		Set<ProcessHandle> toTerminate = ProcessHandle.allProcesses()
			.filter(processHandle ->
				processHandle.info().command().orElse("-").equals(path)
					&& processHandle.pid() != pidCurrent
					&& processHandle.isAlive())
			.collect(Collectors.toSet());
		for (ProcessHandle processHandle : toTerminate)
		{
			processHandle.destroyForcibly();
			try
			{
				// wait for the process to terminate
				processHandle.onExit().get();
			}
			catch (Exception ignored)
			{
			}
		}
		log.debug("Terminated other RuneLite.exe processes.");
	}

	private static void elevateLauncher(String args)
	{
		elevateOrUnelevateLauncher(true, args);
	}

	private static void elevateOrUnelevateLauncher(boolean elevate, String args)
	{
		String name = elevate ? "elevation" : "unelevation";
		if (!nativesLoaded)
		{
			log.debug("Aborting " + name + " request. Launcher natives were not loaded.");
			return;
		}

		String launcherPath = ProcessHandle.current().info().command().orElse(null);
		if (launcherPath == null)
		{
			log.debug("Aborting " + name + " request. launcherPath is null");
			return;
		}

		// either replace \ with \\ or with /
		launcherPath = launcherPath.replace("\\", "/");
		if (elevate)
		{
			// if already running with elevated permissions, no UAC prompt will be shown but the launcher will be
			// relaunched with the requested args (and elevated permissions)
			elevate(launcherPath, args);
		}
		else
		{
			unelevate(launcherPath, args);
		}
		log.debug("Executing " + name + " request");
		System.exit(0);
	}

	private static native void elevate(String launcherPath, String args);

	private static native void unelevate(String launcherPath, String args);

	private static String fixArgs()
	{
		return "--fixfileperms=" + FilePermsStep.increaseStep(filePermsStep);
	}

	private static void fixFilePerms(boolean forceEveryone)
	{
		if (!isRunningElevated())
		{
			// icacls should only be called with elevated permissions; without those, permissions might get fixed for
			// some files but not for others. This results in a dangerous scenario in which the permission verification
			// succeeds but the permissions are still incorrect.
			log.info("Launcher is not running with elevated permissions. Skipping filesystem permission correction.");
			return;
		}

		if (!RUNELITE_DIR.exists() && !RUNELITE_DIR.mkdirs())
		{
			SwingUtilities.invokeLater(() -> new FatalErrorDialog("Unable to create RuneLite directory " + RUNELITE_DIR.getAbsolutePath()
				+ " while running with elevated permissions. Check if your filesystem permissions are correct.").open());
			return;
		}

		String sid = ProcessHandle.current().info().user().orElse(null);
		if (sid == null || forceEveryone)
		{
			sid = "*S-1-1-0";
			// use SID for "Everyone" (compatible with different system languages) if user is not returned
			// or as fallback when setting permissions with the current user is not successful
			// see https://learn.microsoft.com/en-us/windows-server/identity/ad-ds/manage/understand-security-identifiers
			// and https://system32.eventsentry.com/codes/field/Well-known%20Security%20Identifiers%20(SIDs) for more info
		}
		// set normal permissions
		runicaclsCmd("*S-1-5-18"); // NT AUTHORITY\SYSTEM
		runicaclsCmd("*S-1-5-32-544"); // BUILTIN\Administrators
		runicaclsCmd(sid); // current user or everyone

		String dir = "\"" + RUNELITE_DIR.getAbsolutePath() + "\\*.*\"";
		List<String> attribCommands = new ArrayList<>(Arrays.asList("attrib", "-r", "-a", "-s", dir, "/d", "/s"));
		runCmd(attribCommands);

		log.info("Filesystem permissions correction completed for i.a. SID " + sid + " Restarting without elevated permissions to verify.");
		terminateProcesses();
		unelevateLauncher(fixArgs());
	}

	private static void runicaclsCmd(String sid)
	{
		String dir = "\"" + RUNELITE_DIR.getAbsolutePath() + "\"";
		sid = sid + ":F";
		List<String> icaclsCommands = new ArrayList<>(Arrays.asList("icacls", dir, "/grant:r", sid, "/inheritance:r", "/t", "/c"));
		runCmd(icaclsCommands);
	}

	private static void runCmd(List<String> commands)
	{
		String cmdName = commands.get(0);
		List<String> tokens = new ArrayList<>(Arrays.asList("cmd.exe", "/c"));
		tokens.addAll(commands);
		log.info(cmdName + " ProcessBuilder tokens: " + tokens);
		ProcessBuilder cmdPB = new ProcessBuilder(tokens);
		try
		{
			Process cmdProcess = cmdPB.start();
			BufferedReader br = new BufferedReader(new InputStreamReader(cmdProcess.getInputStream()));
			String line;
			log.info(cmdName + " output:");
			while ((line = br.readLine()) != null)
			{
				if (!line.isEmpty())
				{
					log.info(line);
				}
			}
			try
			{
				log.debug(cmdName + " exit value is " + cmdProcess.waitFor());
			}
			catch (InterruptedException ex)
			{
				log.debug(cmdName + " interrupted", ex);
			}
		}
		catch (IOException ex)
		{
			log.error("IOException " + cmdName, ex);
		}
	}

	private static void unelevateLauncher(String args)
	{
		elevateOrUnelevateLauncher(false, args);
	}

	private static void postUserFixVerification()
	{
		if (failedFilePermsVerification())
		{
			log.warn("Filesystem permissions correction failed. Requesting elevated permissions to attempt correction with 'Everyone' SID.");
			requestElevation("Would you like to close all RuneLite instances and restart with elevated permissions to attempt a final correction?");
		}
		else
		{
			log.info("Filesystem permissions corrected successfully.");
			// if not using the Jagex Launcher, RL will load and the user can log in
			if (isJagexLauncherStep())
			{
				suggestJagexLauncher();
			}
		}
	}

	private static boolean isJagexLauncherStep()
	{
		return filePermsStep.getStep() >= FilePermsStep.REGULAR_VERIFICATION_JAGEX_LAUNCHER.getStep();
	}

	private static void suggestJagexLauncher()
	{
		okOptionPane("Filesystem permissions corrected successfully",
			"The filesystem permission problems were corrected successfully.\n"
				+ "Please relaunch RuneLite via the Jagex Launcher.",
			true);
	}

	private static void okOptionPane(String titleAdditions, String message, boolean jagexLauncherSuggestions)
	{
		try (var in = FilePermissionManager.class.getResourceAsStream("runelite_128.png"))
		{
			assert in != null;
			ImageIcon icon = new ImageIcon(ImageIO.read(in));
			icon = new ImageIcon(icon.getImage().getScaledInstance(64, 64, java.awt.Image.SCALE_SMOOTH));
			UIManager.put("OptionPane.background", DARKER_GRAY_COLOR);
			UIManager.put("Panel.background", DARKER_GRAY_COLOR);
			UIManager.put("OptionPane.messageForeground", Color.WHITE);
			String title = titleAdditions.isEmpty() ? "RuneLite" : "RuneLite - " + titleAdditions;

			String[] options = {"Ok"};
			JOptionPane.showOptionDialog(null,
				message,
				title,
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.INFORMATION_MESSAGE,
				icon,
				options,
				null);
		}
		catch (Exception ex)
		{
			String name = jagexLauncherSuggestions ? "Jagex Launcher suggestion" : "Final correction attempt failed";
			log.debug(name + ": error showing JOptionPane.", ex);
		}
		if (jagexLauncherSuggestions)
		{
			System.exit(0);
		}
	}

	private static void postEveryoneFixVerification()
	{
		if (failedFilePermsVerification())
		{
			log.warn("Filesystem permissions correction failed with 'Everyone' SID. No further attempts will be made during this session.");
			logFilePerms();
			if (isJagexLauncherStep())
			{
				okOptionPane("Filesystem permission problems detected",
					"Filesystem permissions correction failed.\n"
						+ "Please relaunch RuneLite via the Jagex Launcher.",
					true);
			}
			else
			{
				okOptionPane("Filesystem permission problems detected",
					"Filesystem permissions correction failed.\n" +
						"No further attempts will be made during this session.",
					false);
			}
		}
		else
		{
			log.info("Filesystem permissions corrected successfully.");
			// if not using the Jagex Launcher, RL will load and the user can log in
			if (isJagexLauncherStep())
			{
				suggestJagexLauncher();
			}
		}
	}

	private static void logFilePerms()
	{
		// log file perms using icacls to more easily provide support to the user in case the fixes fail
		log.info("icacls filesystem permissions for support purposes:");
		icaclsLogCmd(RUNELITE_DIR);
		icaclsLogCmd(LOGS_DIR);
		icaclsLogCmd(REPO_DIR);
		icaclsLogCmd(PROFILES_DIR);
	}

	private static void icaclsLogCmd(File directory)
	{
		String dir = "\"" + directory.getAbsolutePath() + "\"";
		List<String> icaclsCommands = new ArrayList<>(Arrays.asList("icacls", dir));
		runCmd(icaclsCommands);
	}
}

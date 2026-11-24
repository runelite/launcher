/*
 * Copyright (c) 2026, Nick <nickspaghettidev@gmail.com>
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

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Taskbar;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class DockMenu
{
	private static final String NEW_INSTANCE = "New Instance";

	// Adds a "New Instance" item to the dock icon's context menu.
	//
	// This has to be called from the process which owns the dock icon for the session. In FORK launch
	// mode that is the forked child, not the parent launcher, which exits as soon as it has forked.
	static void install(@Nullable List<String> newInstanceCommand)
	{
		if (OS.getOs() != OS.OSType.MacOS)
		{
			return;
		}

		if (newInstanceCommand == null || newInstanceCommand.isEmpty())
		{
			log.debug("Not installing dock menu, unable to determine how to launch a new instance");
			return;
		}

		try
		{
			if (!Taskbar.isTaskbarSupported())
			{
				log.debug("Not installing dock menu, taskbar is unsupported");
				return;
			}

			var taskbar = Taskbar.getTaskbar();
			if (!taskbar.isSupported(Taskbar.Feature.MENU))
			{
				log.debug("Not installing dock menu, menu feature is unsupported");
				return;
			}

			var item = new MenuItem(NEW_INSTANCE);
			item.addActionListener(ev -> newInstance(newInstanceCommand));

			var menu = new PopupMenu();
			menu.add(item);
			taskbar.setMenu(menu);

			log.debug("Installed dock menu, new instance command: {}", newInstanceCommand);
		}
		catch (Exception ex)
		{
			// the dock menu is cosmetic, so it must never prevent the client from starting
			log.warn("unable to install dock menu", ex);
		}
	}

	/**
	 * {@link ProcessBuilder#start()} forks, so it is kept off the Event Dispatch Thread. Unlike
	 * {@link ForkLauncher#launch}, this process outlives the one it starts, so the child's output is
	 * discarded.
	 */
	private static void newInstance(List<String> command)
	{
		log.info("Launching new instance: {}", command);

		var thread = new Thread(() ->
		{
			try
			{
				new ProcessBuilder(command)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			}
			catch (IOException ex)
			{
				log.error("unable to launch new instance", ex);
			}
		});
		thread.setName(NEW_INSTANCE);
		thread.setDaemon(true);
		thread.start();
	}
}

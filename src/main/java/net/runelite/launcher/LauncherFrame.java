/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
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

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LauncherFrame extends JFrame
{
	private final JProgressBar bar;

	public LauncherFrame()
	{
		this.setTitle("RuneLite");
		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		this.setSize(300, 70);
		this.setLayout(new BorderLayout());
		this.setUndecorated(true);

		bar = new JProgressBar();
		bar.setMaximum(100);
		bar.setStringPainted(true);
		bar.setSize(300, 70);
		bar.setPreferredSize(new Dimension(300, 70));
		bar.setVisible(true);
		add(bar, BorderLayout.CENTER);
		pack();

		this.setLocationRelativeTo(null);
		this.setVisible(true);
	}

	void progress(String filename, int bytes, int total)
	{
		if (total == 0)
		{
			return;
		}

		int percent = (int) (((float) bytes / (float) total) * 100f);
		bar.setString(filename + " (" + percent + "%)");
		bar.setValue(percent);
	}
}

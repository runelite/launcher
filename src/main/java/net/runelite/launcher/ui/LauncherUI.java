package net.runelite.launcher.ui;

import javax.swing.JFrame;
import javax.swing.JLabel;

public class LauncherUI extends JFrame
{
	public LauncherUI()
	{
		//pack();
		this.setTitle("Runelite Launcher");
		//setLocationRelativeTo(getOwner());
		this.setSize(400, 100);
		this.setVisible(true);

		JLabel label = new JLabel("Downloading stuff...");
		add(label);

		this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
	}
}

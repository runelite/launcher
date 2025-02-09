/*
 * Copyright (c) 2023, Adam <Adam@sigterm.info>
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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.awt.Color;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigurationFrame extends JFrame
{
	private static final Color DARKER_GRAY_COLOR = new Color(30, 30, 30);

	private final JCheckBox chkboxDebug;
	private final JCheckBox chkboxNoDiffs;
	private final JCheckBox chkboxSkipTlsVerification;
	private final JCheckBox chkboxNoUpdates;
	private final JCheckBox chkboxSafemode;
	private final JCheckBox chkboxIpv4;
	private final JTextField txtScale;
	private final JTextArea txtClientArguments;
	private final JTextArea txtJvmArguments;
	private final JComboBox<HardwareAccelerationMode> comboHardwareAccelMode;
	private final JComboBox<LaunchMode> comboLaunchMode;

	private ConfigurationFrame(LauncherSettings settings)
	{
		setTitle("RuneLite Launcher Configuration");

		BufferedImage iconImage;
		try (var in = ConfigurationFrame.class.getResourceAsStream(LauncherProperties.getRuneLite128()))
		{
			iconImage = ImageIO.read(in);
		}
		catch (IOException ex)
		{
			throw new RuntimeException(ex);
		}

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setIconImage(iconImage);

		Container pane = getContentPane();
		pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
		pane.setBackground(DARKER_GRAY_COLOR);

		var topPanel = new JPanel();
		topPanel.setBackground(DARKER_GRAY_COLOR);
		topPanel.setLayout(new GridLayout(3, 2, 0, 0));
		topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		topPanel.add(chkboxDebug = checkbox(
			"Debug",
			"Runs the launcher and client in debug mode. Debug mode writes debug level logging to the log files.",
			Boolean.TRUE.equals(settings.debug)
		));

		topPanel.add(chkboxNoDiffs = checkbox(
			"Disable diffs",
			"Downloads full artifacts for updates instead of diffs.",
			Boolean.TRUE.equals(settings.nodiffs)
		));

		topPanel.add(chkboxSkipTlsVerification = checkbox(
			"Disable TLS verification",
			"Disables TLS verification.",
			Boolean.TRUE.equals(settings.skipTlsVerification)
		));

		topPanel.add(chkboxNoUpdates = checkbox(
			"Disable updates",
			"Disables the launcher self updating",
			Boolean.TRUE.equals(settings.noupdates)
		));

		topPanel.add(chkboxSafemode = checkbox(
			"Safe mode",
			"Launches the client in safe mode",
			Boolean.TRUE.equals(settings.safemode)
		));

		topPanel.add(chkboxIpv4 = checkbox(
			"IPv4",
			"Prefer IPv4 over IPv6",
			Boolean.TRUE.equals(settings.ipv4)
		));

		pane.add(topPanel);

		var midPanel = new JPanel();
		midPanel.setBackground(DARKER_GRAY_COLOR);
		midPanel.setLayout(new GridLayout(2, 2, 0, 0));

		midPanel.add(label(
			"Client arguments",
			"Arguments passed to the client. One per line."
		));

		var sp = new JScrollPane(txtClientArguments = area(Joiner.on('\n').join(settings.clientArguments)),
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		midPanel.add(sp);

		midPanel.add(label(
			"JVM arguments",
			"Arguments passed to the JVM. One per line."
		));

		sp = new JScrollPane(txtJvmArguments = area(Joiner.on('\n').join(settings.jvmArguments)),
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		midPanel.add(sp);

		pane.add(midPanel);

		var bottomPanel = new JPanel();
		bottomPanel.setBackground(DARKER_GRAY_COLOR);
		bottomPanel.setLayout(new GridLayout(3, 2, 0, 0));

		bottomPanel.add(label(
			"Scale",
			"Scaling factor for Java 2D"
		));
		bottomPanel.add(txtScale = field(settings.scale != null ? Double.toString(settings.scale) : null));

		bottomPanel.add(label(
			"Hardware acceleration",
			"Hardware acceleration mode for Java 2D."
		));
		bottomPanel.add(comboHardwareAccelMode = combobox(
			HardwareAccelerationMode.values(),
			settings.hardwareAccelerationMode
		));

		bottomPanel.add(label("Launch mode", null));
		bottomPanel.add(comboLaunchMode = combobox(
			LaunchMode.values(),
			settings.launchMode
		));

		pane.add(bottomPanel);

		var buttonPanel = new JPanel();
		buttonPanel.setBackground(DARKER_GRAY_COLOR);

		var save = new JButton("Save");
		save.addActionListener(this::save);
		buttonPanel.add(save);

		var cancel = new JButton("Cancel");
		cancel.addActionListener(l -> dispose());
		buttonPanel.add(cancel);

		pane.add(buttonPanel);

		pack();
		setLocationRelativeTo(null);
		setMinimumSize(getSize());
	}

	private void save(ActionEvent l)
	{
		var settings = LauncherSettings.loadSettings();
		settings.debug = chkboxDebug.isSelected();
		settings.nodiffs = chkboxNoDiffs.isSelected();
		settings.skipTlsVerification = chkboxSkipTlsVerification.isSelected();
		settings.noupdates = chkboxNoUpdates.isSelected();
		settings.safemode = chkboxSafemode.isSelected();
		settings.ipv4 = chkboxIpv4.isSelected();

		var t = txtScale.getText();
		settings.scale = null;
		if (!t.isEmpty())
		{
			try
			{
				settings.scale = Double.parseDouble(t);
			}
			catch (NumberFormatException ignored)
			{
			}
		}

		settings.clientArguments = Splitter.on('\n')
			.omitEmptyStrings()
			.trimResults()
			.splitToList(txtClientArguments.getText());

		settings.jvmArguments = Splitter.on('\n')
			.omitEmptyStrings()
			.trimResults()
			.splitToList(txtJvmArguments.getText());

		settings.hardwareAccelerationMode = (HardwareAccelerationMode) comboHardwareAccelMode.getSelectedItem();
		settings.launchMode = (LaunchMode) comboLaunchMode.getSelectedItem();

		LauncherSettings.saveSettings(settings);

		// IPv4 change requires patching packr config
		PackrConfig.patch(config ->
		{
			List<String> vmArgs = (List) config.computeIfAbsent("vmArgs", k -> new ArrayList<>());
			if (settings.ipv4)
			{
				vmArgs.add("-Djava.net.preferIPv4Stack=true");
			}
			else
			{
				vmArgs.remove("-Djava.net.preferIPv4Stack=true");
			}
		});

		log.info("Updated launcher configuration:" + System.lineSeparator() + "{}", settings.configurationStr());

		dispose();
	}

	private static JLabel label(String name, String tooltip)
	{
		var label = new JLabel(name);
		label.setToolTipText(tooltip);
		label.setForeground(Color.WHITE);
		return label;
	}

	private static JTextField field(@Nullable String value)
	{
		return new JTextField(value);
	}

	private static JTextArea area(@Nullable String value)
	{
		return new JTextArea(value, 2, 20);
	}

	private static JCheckBox checkbox(String name, String tooltip, boolean checked)
	{
		var checkbox = new JCheckBox(name);
		checkbox.setSelected(checked);
		checkbox.setToolTipText(tooltip);
		checkbox.setForeground(Color.WHITE);
		checkbox.setBackground(DARKER_GRAY_COLOR);
		return checkbox;
	}

	private static <E> JComboBox<E> combobox(E[] values, E default_)
	{
		var combobox = new JComboBox<>(values);
		combobox.setSelectedItem(default_);
		return combobox;
	}

	static void open()
	{
		new ConfigurationFrame(LauncherSettings.loadSettings())
			.setVisible(true);
	}

	public static void main(String[] args)
	{
		open();
	}
}

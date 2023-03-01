/*
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
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

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum HardwareAccelerationMode
{
	AUTO,
	OFF,
	DIRECTDRAW,
	OPENGL,
	METAL;

	/**
	 * Gets list of JVM properties to enable Hardware Acceleration for this mode.
	 * See https://docs.oracle.com/javase/8/docs/technotes/guides/2d/flags.html for reference
	 * @return list of params
	 */
	public Map<String, String> toParams(OS.OSType os)
	{
		final Map<String, String> params = new LinkedHashMap<>();

		switch (this)
		{
			case DIRECTDRAW:
				if (os != OS.OSType.Windows)
				{
					throw new IllegalArgumentException("Directdraw is only available on Windows");
				}

				params.put("sun.java2d.d3d", "true");
				// The opengl prop overrides the d3d prop, so explicitly disable it
				params.put("sun.java2d.opengl", "false");
				break;
			case OPENGL:
				if (os == OS.OSType.Windows)
				{
					// I don't think this is necessary, but historically we've had it here anyway
					params.put("sun.java2d.d3d", "false");
				}
				else if (os == OS.OSType.MacOS)
				{
					// The metal prop overrides the opengl prop, so explicitly disable it
					params.put("sun.java2d.metal", "false");
				}

				params.put("sun.java2d.opengl", "true");
				break;
			case OFF:
				if (os == OS.OSType.Windows)
				{
					params.put("sun.java2d.d3d", "false");
				}
				else if (os == OS.OSType.MacOS)
				{
					// Prior to 17, the j2d properties are not checked on MacOS and OpenGL is always used. 17 requires
					// either OpenGL or Metal to be enabled.
					throw new IllegalArgumentException("Hardware acceleration mode on MacOS must be one of OPENGL or METAL");
				}

				params.put("sun.java2d.opengl", "false");
				// Unix also has sun.java2d.xrender, which defaults to true, but I've never seen it cause problems
				break;
			case METAL:
				if (os != OS.OSType.MacOS)
				{
					throw new IllegalArgumentException("Metal is only available on MacOS");
				}

				params.put("sun.java2d.metal", "true");
				break;
		}

		return params;
	}

	public static HardwareAccelerationMode defaultMode(OS.OSType osType)
	{
		switch (osType)
		{
			case Windows:
				return HardwareAccelerationMode.DIRECTDRAW;
			case MacOS:
				return HardwareAccelerationMode.OPENGL;
			case Linux:
			default:
				return HardwareAccelerationMode.OFF;
		}
	}
}
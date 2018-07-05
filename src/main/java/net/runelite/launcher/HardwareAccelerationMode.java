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

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum HardwareAccelerationMode
{
	OFF,
	DIRECTDRAW,
	OPENGL;

	/**
	 * Gets list of JVM properties to enable Hardware Acceleration for this mode.
	 * See https://docs.oracle.com/javase/8/docs/technotes/guides/2d/flags.html for reference
	 * @return list of params
	 */
	public List<String> toParams()
	{
		final List<String> params = new ArrayList<>();

		switch (this)
		{
			case DIRECTDRAW:
				params.add("-Dsun.java2d.noddraw=false");
				params.add("-Dsun.java2d.opengl=false");
				break;
			case OPENGL:
				params.add("-Dsun.java2d.noddraw=true");
				params.add("-Dsun.java2d.opengl=true");
				break;
			case OFF:
				params.add("-Dsun.java2d.noddraw=true");
				params.add("-Dsun.java2d.opengl=false");
				break;
		}

		return params;
	}
}
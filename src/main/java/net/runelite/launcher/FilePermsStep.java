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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum FilePermsStep
{
	REGULAR_VERIFICATION(0),
	FIX_USER(1),
	VERIFY_POST_USER(2),
	FIX_EVERYONE(3),
	VERIFY_POST_EVERYONE(4),
	// >= 10 is the same as 0-9 but the launcher was initially launched by the Jagex Launcher
	REGULAR_VERIFICATION_JAGEX_LAUNCHER(10),
	FIX_USER_JAGEX_LAUNCHER(11),
	VERIFY_POST_USER_JAGEX_LAUNCHER(12),
	FIX_EVERYONE_JAGEX_LAUNCHER(13),
	VERIFY_POST_EVERYONE_JAGEX_LAUNCHER(14);

	private final int step;

	private boolean compare(int i)
	{
		return step == i;
	}

	private static FilePermsStep getValue(int step)
	{
		for (FilePermsStep filePermsStep : FilePermsStep.values())
		{
			if (filePermsStep.compare(step))
			{
				return filePermsStep;
			}
		}
		return FilePermsStep.REGULAR_VERIFICATION;
	}

	static FilePermsStep increaseStep(FilePermsStep filePermsStep)
	{
		return getValue(filePermsStep.getStep() + 1);
	}
}

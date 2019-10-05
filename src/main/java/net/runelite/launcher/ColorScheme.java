/*
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
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

/**
 * This class serves to hold commonly used UI colors.
 */
class ColorScheme
{
	/* The blue color used for the branding's accents */
	static final Color BRAND_BLUE = new Color(25, 194, 255);

	/* The blue color used for the branding's accents, with lowered opacity */
	static final Color BRAND_BLUE_TRANSPARENT = new Color(25, 194, 255, 120);


	static final Color DARK_GRAY_COLOR = new Color(40, 40, 40);
	static final Color DARKER_GRAY_COLOR = new Color(30, 30, 30);
	static final Color MEDIUM_GRAY_COLOR = new Color(77, 77, 77);

	/* The background color of the scrollbar's track */
	static final Color SCROLL_TRACK_COLOR = new Color(25, 25, 25);

	/* The color for the red progress bar (used in ge offers, farming tracker, etc)*/
	static final Color PROGRESS_ERROR_COLOR = new Color(230, 30, 30);
}
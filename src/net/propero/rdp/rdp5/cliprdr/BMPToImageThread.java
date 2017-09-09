/* BMPToImageThread.java
 * Component: ProperJavaRDP
 *
 * Copyright (c) 2005 Propero Limited
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 *
 * (See gpl.txt for details of the GNU General Public License.)
 *
 */
package net.propero.rdp.rdp5.cliprdr;

import java.awt.Image;
import java.io.ByteArrayInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.propero.rdp.RdpPacket;

public class BMPToImageThread extends Thread {

	RdpPacket data;

	int length;

	ClipInterface c;

	private static final Logger LOGGER = LogManager.getLogger();

	public BMPToImageThread(RdpPacket data, int length, ClipInterface c) {
		super();
		this.data = data;
		this.length = length;
		this.c = c;
	}

	@Override
	public void run() {
		int origin = data.getPosition();

		data.getLittleEndian32(); // head_len

		data.setPosition(origin);

		byte[] content = new byte[length];

		for (int i = 0; i < length; i++) {
			content[i] = (byte) (data.get8() & 0xFF);
		}

		try {
			Image img = ClipBMP.loadbitmap(new ByteArrayInputStream(content));
			ImageSelection imageSelection = new ImageSelection(img);
			c.copyToClipboard(imageSelection);
		} catch (Exception ex) {
			LOGGER.warn("Failed to load bitmap", ex);
		}
	}

}

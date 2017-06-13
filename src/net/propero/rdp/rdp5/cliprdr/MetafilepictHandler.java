/* MetafilepictHandler.java
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

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.propero.rdp.RdpPacket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MetafilepictHandler extends TypeHandler {
	private static final Logger LOGGER = LogManager.getLogger();

	/* Mapping Modes */
	public static final int MM_TEXT = 1;

	public static final int MM_LOMETRIC = 2;

	public static final int MM_HIMETRIC = 3;

	public static final int MM_LOENGLISH = 4;

	public static final int MM_HIENGLISH = 5;

	public static final int MM_TWIPS = 6;

	public static final int MM_ISOTROPIC = 7;

	public static final int MM_ANISOTROPIC = 8;

	String[] mapping_modes = { "undefined", "MM_TEXT", "MM_LOMETRIC",
			"MM_HIMETRIC", "MM_LOENGLISH", "MM_HIENGLISH", "MM_TWIPS",
			"MM_ISOTROPIC", "MM_ANISOTROPIC" };

	@Override
	public boolean formatValid(int format) {
		return (format == CF_METAFILEPICT);
	}

	@Override
	public boolean mimeTypeValid(String mimeType) {
		return mimeType.equals("image");
	}

	@Override
	public int preferredFormat() {
		return CF_METAFILEPICT;
	}

	public Transferable handleData(RdpPacket data, int length) {
		String thingy = "";

		int mm = data.getLittleEndian32();
		int width = data.getLittleEndian32();
		int height = data.getLittleEndian32();
		LOGGER.debug("Metafile mapping mode = {} ({}), width={}, height={}", mapping_modes[mm], mm, width, height);

		try (OutputStream out = new FileOutputStream("test.wmf")) {

			for (int i = 0; i < (length - 12); i++) {
				int aByte = data.get8();
				out.write(aByte);
				thingy += Integer.toHexString(aByte & 0xFF) + " ";
			}
			// System.out.println(thingy);
		} catch (FileNotFoundException e) {
			LOGGER.warn("Failed to write(!?) test.wmf", e);
		} catch (IOException e) {
			LOGGER.warn("Failed to write(!?) test.wmf", e);
		}
		return (new StringSelection(thingy));
	}

	@Override
	public String name() {
		return "CF_METAFILEPICT";
	}

	public byte[] fromTransferable(Transferable in) {
		return null;
	}

	@Override
	public void handleData(RdpPacket data, int length, ClipInterface c) {
		int mm = data.getLittleEndian32();
		int width = data.getLittleEndian32();
		int height = data.getLittleEndian32();
		LOGGER.debug("Metafile mapping mode = {} ({}), width={}, height={}", mapping_modes[mm], mm, width, height);

		try (OutputStream out = new FileOutputStream("test.wmf")) {
			StringBuilder thingy = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);
			for (int i = 0; i < (length - 12); i++) {
				int aByte = data.get8();
				out.write(aByte);
				if (thingy != null) {
					thingy.append(Integer.toHexString(aByte & 0xFF)).append(' ');
				}
			}
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(thingy);
			}
		} catch (FileNotFoundException e) {
			LOGGER.warn("Failed to write(!?) test.wmf", e);
		} catch (IOException e) {
			LOGGER.warn("Failed to write(!?) test.wmf", e);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.propero.rdp.rdp5.cliprdr.TypeHandler#send_data(java.awt.datatransfer.Transferable,
	 *      net.propero.rdp.rdp5.cliprdr.ClipInterface)
	 */
	@Override
	public void send_data(Transferable in, ClipInterface c) {
		c.send_null(ClipChannel.CLIPRDR_DATA_RESPONSE,
				ClipChannel.CLIPRDR_ERROR);
	}

}

/* Options.java
 * Component: ProperJavaRDP
 * 
 * Revision: $Revision$
 * Author: $Author$
 * Date: $Date$
 *
 * Copyright (c) 2005 Propero Limited
 *
 * Purpose: Global static storage of user-definable options
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
package net.propero.rdp;

import java.awt.image.DirectColorModel;

@Deprecated
public class Options {

	public static final int DIRECT_BITMAP_DECOMPRESSION = 0;

	public static final int BUFFEREDIMAGE_BITMAP_DECOMPRESSION = 1;

	public static final int INTEGER_BITMAP_DECOMPRESSION = 2;

	@Deprecated
	public static int bitmap_decompression_store = INTEGER_BITMAP_DECOMPRESSION;

	// disables bandwidth saving tcp packets
	@Deprecated
	public static boolean low_latency = true;

	@Deprecated
	public static int keylayout = 0x809; // UK by default

	@Deprecated
	public static String username = "Administrator"; // -u username

	@Deprecated
	public static String domain = ""; // -d domain

	@Deprecated
	public static String password = ""; // -p password

	@Deprecated
	public static String hostname = ""; // -n hostname

	@Deprecated
	public static String command = ""; // -s command

	@Deprecated
	public static String directory = ""; // -d directory

	@Deprecated
	public static String windowTitle = "properJavaRDP"; // -T windowTitle

	@Deprecated
	public static int width = 800; // -g widthxheight

	@Deprecated
	public static int height = 600; // -g widthxheight

	@Deprecated
	public static int port = 3389; // -t port

	@Deprecated
	public static boolean fullscreen = false;

	@Deprecated
	public static boolean built_in_licence = false;

	@Deprecated
	public static boolean load_licence = false;

	@Deprecated
	public static boolean save_licence = false;

	@Deprecated
	public static String licence_path = "./";

	@Deprecated
	public static boolean debug_keyboard = false;

	@Deprecated
	public static boolean debug_hexdump = false;

	@Deprecated
	public static boolean enable_menu = false;

	// public static boolean paste_hack = true;

	@Deprecated
	public static boolean altkey_quiet = false;

	@Deprecated
	public static boolean caps_sends_up_and_down = true;

	@Deprecated
	public static boolean remap_hash = true;

	@Deprecated
	public static boolean useLockingKeyState = true;

	@Deprecated
	public static boolean use_rdp5 = true;

	@Deprecated
	public static int server_bpp = 24; // Bits per pixel

	@Deprecated
	public static int Bpp = (server_bpp + 7) / 8; // Bytes per pixel

	// Correction value to ensure only the relevant number of bytes are used for
	// a pixel
	@Deprecated
	public static int bpp_mask = 0xFFFFFF >> 8 * (3 - Bpp);

	@Deprecated
	public static int imgCount = 0;

	@Deprecated
	public static DirectColorModel colour_model = new DirectColorModel(24,
			0xFF0000, 0x00FF00, 0x0000FF);

	/**
	 * Set a new value for the server's bits per pixel
	 * 
	 * @param server_bpp
	 *            New bpp value
	 */
	@Deprecated
	public static void set_bpp(int server_bpp) {
		Options.server_bpp = server_bpp;
		Options.Bpp = (server_bpp + 7) / 8;
		if (server_bpp == 8)
			bpp_mask = 0xFF;
		else
			bpp_mask = 0xFFFFFF;

		colour_model = new DirectColorModel(24, 0xFF0000, 0x00FF00, 0x0000FF);
	}

	@Deprecated
	public static int server_rdp_version;

	@Deprecated
	public static int win_button_size = 0; /* If zero, disable single app mode */

	@Deprecated
	public static boolean bitmap_compression = true;

	@Deprecated
	public static boolean persistent_bitmap_caching = false;

	@Deprecated
	public static boolean bitmap_caching = false;

	@Deprecated
	public static boolean precache_bitmaps = false;

	@Deprecated
	public static boolean polygon_ellipse_orders = false;

	@Deprecated
	public static boolean sendmotion = true;

	@Deprecated
	public static boolean orders = true;

	@Deprecated
	public static boolean encryption = true;

	@Deprecated
	public static boolean packet_encryption = true;

	@Deprecated
	public static boolean desktop_save = true;

	@Deprecated
	public static boolean grab_keyboard = true;

	@Deprecated
	public static boolean hide_decorations = false;

	@Deprecated
	public static boolean console_session = false;

	@Deprecated
	public static boolean owncolmap;

	@Deprecated
	public static boolean use_ssl = false;

	@Deprecated
	public static boolean map_clipboard = true;

	@Deprecated
	public static int rdp5_performanceflags = Rdp.RDP5_NO_CURSOR_SHADOW
			| Rdp.RDP5_NO_CURSORSETTINGS | Rdp.RDP5_NO_FULLWINDOWDRAG
			| Rdp.RDP5_NO_MENUANIMATIONS | Rdp.RDP5_NO_THEMING
			| Rdp.RDP5_NO_WALLPAPER;

	@Deprecated
	public static boolean save_graphics = false;

}

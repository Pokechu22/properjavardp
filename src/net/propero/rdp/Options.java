/* options.java
 * Component: ProperJavaRDP
 * 
 * Revision: $Revision$
 * Author: $Author$
 * Date: $Date$
 *
 * Copyright (c) 2005 Propero Limited
 *
 * Purpose: <del>Global static</del> storage of user-definable options
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

public class Options {

	public static final int DIRECT_BITMAP_DECOMPRESSION = 0;

	public static final int BUFFEREDIMAGE_BITMAP_DECOMPRESSION = 1;

	public static final int INTEGER_BITMAP_DECOMPRESSION = 2;

	public int bitmap_decompression_store = INTEGER_BITMAP_DECOMPRESSION;

	// disables bandwidth saving tcp packets
	public boolean low_latency = true;

	public int keylayout = 0x809; // UK by default

	public String username = "Administrator"; // -u username

	public String domain = ""; // -d domain

	public String password = ""; // -p password

	public String hostname = ""; // -n hostname

	public String command = ""; // -s command

	public String directory = ""; // -d directory

	public String windowTitle = "properJavaRDP"; // -T windowTitle

	public int width = 800; // -g widthxheight

	public int height = 600; // -g widthxheight

	public int port = 3389; // -t port

	public boolean fullscreen = false;

	public boolean built_in_licence = false;

	public boolean load_licence = false;

	public boolean save_licence = false;

	public String licence_path = "./";

	// Controls one flag within License; I don't entirely get it
	public boolean use_real_license_signature = true;

	public boolean debug_keyboard = false;

	public boolean debug_hexdump = false;

	public boolean enable_menu = false;

	// public static boolean paste_hack = true;

	public boolean altkey_quiet = false;

	public boolean caps_sends_up_and_down = true;

	public boolean remap_hash = true;

	public boolean useLockingKeyState = true;

	public boolean use_rdp5 = true;

	public int server_bpp = 24; // Bits per pixel

	public int Bpp = (server_bpp + 7) / 8; // Bytes per pixel

	// Correction value to ensure only the relevant number of bytes are used for
	// a pixel
	public int bpp_mask = 0xFFFFFF >> 8 * (3 - Bpp);

	public int imgCount = 0;

	public DirectColorModel colour_model = new DirectColorModel(24,
			0xFF0000, 0x00FF00, 0x0000FF);

	/**
	 * Set a new value for the server's bits per pixel
	 * 
	 * @param server_bpp
	 *            New bpp value
	 */
	public void set_bpp(int server_bpp) {
		this.server_bpp = server_bpp;
		this.Bpp = (server_bpp + 7) / 8;
		if (server_bpp == 8)
			bpp_mask = 0xFF;
		else
			bpp_mask = 0xFFFFFF;

		colour_model = new DirectColorModel(24, 0xFF0000, 0x00FF00, 0x0000FF);
	}

	public int server_rdp_version;

	public int win_button_size = 0; /* If zero, disable single app mode */

	public boolean bitmap_compression = true;

	public boolean persistent_bitmap_caching = false;

	public boolean bitmap_caching = false;

	public boolean precache_bitmaps = false;

	public boolean polygon_ellipse_orders = false;

	public boolean sendmotion = true;

	public boolean orders = true;

	public boolean encryption = true;

	public boolean packet_encryption = true;

	public boolean desktop_save = true;

	public boolean grab_keyboard = true;

	public boolean hide_decorations = false;

	public boolean console_session = false;

	public boolean owncolmap;

	public boolean use_ssl = false;

	public boolean map_clipboard = true;

	public int rdp5_performanceflags = Rdp.RDP5_NO_CURSOR_SHADOW
			| Rdp.RDP5_NO_CURSORSETTINGS | Rdp.RDP5_NO_FULLWINDOWDRAG
			| Rdp.RDP5_NO_MENUANIMATIONS | Rdp.RDP5_NO_THEMING
			| Rdp.RDP5_NO_WALLPAPER;

	public boolean save_graphics = false;

	// -------------------------

	// Previously under Common.  True if run in an applet.
	// When set, System.exit should not be used.
	public boolean noSystemExit;

	// Previously under Constants
	public OS os = OS.UNKNOWN;
	public static enum OS {
		UNKNOWN,
		WINDOWS,
		MAC,
		LINUX
	}
}

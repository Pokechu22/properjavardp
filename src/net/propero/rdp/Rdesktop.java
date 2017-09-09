/* Rdesktop.java
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

package net.propero.rdp;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import net.propero.rdp.DisconnectInfo.Reason;
import net.propero.rdp.api.InitState;
import net.propero.rdp.keymapping.KeyCode_FileBased;
import net.propero.rdp.ui.RdesktopFrame;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main class, launches session
 */
public class Rdesktop {

	private static final Logger LOGGER = LogManager.getLogger();

	public static final String keyMapPath = "keymaps/";

	/**
	 * Outputs version and usage information via System.err
	 *
	 */
	public static void usage() {
		LOGGER.info("properJavaRDP version " + Version.version);
		LOGGER.info("Usage: java net.propero.rdp.Rdesktop [options] server[:port]");
		LOGGER.info("	-b 							bandwidth saving (good for 56k modem, but higher latency");
		LOGGER.info("	-c DIR						working directory");
		LOGGER.info("	-d DOMAIN					logon domain");
		LOGGER.info("	-f[l]						full-screen mode [with Linux KDE optimization]");
		LOGGER.info("	-g WxH						desktop geometry");
		LOGGER.info("	-m MAPFILE					keyboard mapping file for terminal server");
		LOGGER.info("	-l LEVEL					logging level {DEBUG, INFO, WARN, ERROR, FATAL}");
		LOGGER.info("	-n HOSTNAME					client hostname");
		LOGGER.info("	-p PASSWORD					password");
		LOGGER.info("	-s SHELL					shell");
		LOGGER.info("	-t NUM						RDP port (default 3389)");
		LOGGER.info("	-T TITLE					window title");
		LOGGER.info("	-u USERNAME					user name");
		LOGGER.info("	-o BPP						bits-per-pixel for display");
		LOGGER.info("	-r path						path to load licence from (requests and saves licence from server if not found)");
		LOGGER.info("	--save_licence				request and save licence from server");
		LOGGER.info("	--load_licence				load licence from file");
		LOGGER.info("	--console					connect to console");
		LOGGER.info("	--debug_key 				show scancodes sent for each keypress etc");
		LOGGER.info("	--debug_hex 				show bytes sent and received");
		LOGGER.info("	--no_remap_hash 			disable hash remapping");
		LOGGER.info("	--quiet_alt 				enable quiet alt fix");
		LOGGER.info("	--no_encryption				disable encryption from client to server");
		// logger.info("	--enable_menu				enable menu bar");
		LOGGER.info("	--log4j_config=FILE			use FILE for log4j configuration");
		LOGGER.info("Example: java net.propero.rdp.Rdesktop -g 800x600 -l WARN m52.propero.int");
		Rdesktop.exit(0, null, null, true);
	}

	/**
	 *
	 * @param args
	 * @throws OrderException
	 * @throws RdesktopException
	 */
	public static void main(String[] args) throws RdesktopException {
		String mapFile = "en-us";

		Options options = new Options();

		// parse arguments

		int logonflags = Rdp.RDP_LOGON_NORMAL;

		boolean fKdeHack = false;
		int c;
		String arg;
		StringBuffer sb = new StringBuffer();
		LongOpt[] alo = new LongOpt[11];
		alo[0] = new LongOpt("debug_key", LongOpt.NO_ARGUMENT, null, 0);
		alo[1] = new LongOpt("debug_hex", LongOpt.NO_ARGUMENT, null, 0);
		alo[2] = new LongOpt("no_paste_hack", LongOpt.NO_ARGUMENT, null, 0);
		alo[3] = new LongOpt("log4j_config", LongOpt.REQUIRED_ARGUMENT, sb, 0);
		alo[4] = new LongOpt("quiet_alt", LongOpt.NO_ARGUMENT, sb, 0);
		alo[5] = new LongOpt("no_remap_hash", LongOpt.NO_ARGUMENT, null, 0);
		alo[6] = new LongOpt("enable_menu", LongOpt.NO_ARGUMENT, null, 0);
		alo[7] = new LongOpt("console", LongOpt.NO_ARGUMENT, null, 0);
		alo[8] = new LongOpt("load_licence", LongOpt.NO_ARGUMENT, null, 0);
		alo[9] = new LongOpt("save_licence", LongOpt.NO_ARGUMENT, null, 0);
		alo[10] = new LongOpt("persistent_caching", LongOpt.NO_ARGUMENT, null,
				0);

		Getopt g = new Getopt("properJavaRDP", args,
				"bc:d:f::g:k:l:m:n:p:s:t:T:u:o:r:", alo);

		while ((c = g.getopt()) != -1) {
			switch (c) {

			case 0:
				switch (g.getLongind()) {
				case 0:
					options.debug_keyboard = true;
					break;
				case 1:
					options.debug_hexdump = true;
					break;
				case 2:
					break;
				case 3:
					throw new UnsupportedOperationException("Use -Dlog4j.configurationFile instead of setting log config");
				case 4:
					options.altkey_quiet = true;
					break;
				case 5:
					options.remap_hash = false;
					break;
				case 6:
					options.enable_menu = true;
					break;
				case 7:
					options.console_session = true;
					break;
				case 8:
					options.load_licence = true;
					break;
				case 9:
					options.save_licence = true;
					break;
				case 10:
					options.persistent_bitmap_caching = true;
					break;
				default:
					usage();
					return;
				}
				break;

			case 'o':
				options.set_bpp(Integer.parseInt(g.getOptarg()));
				break;
			case 'b':
				options.low_latency = false;
				break;
			case 'm':
				mapFile = g.getOptarg();
				break;
			case 'c':
				options.directory = g.getOptarg();
				break;
			case 'd':
				options.domain = g.getOptarg();
				break;
			case 'f':
				Dimension screen_size = Toolkit.getDefaultToolkit()
				.getScreenSize();
				// ensure width a multiple of 4
				options.width = screen_size.width & ~3;
				options.height = screen_size.height;
				options.fullscreen = true;
				arg = g.getOptarg();
				if (arg != null) {
					if (arg.charAt(0) == 'l') {
						fKdeHack = true;
					} else {
						LOGGER.fatal("properJavaRDP: Invalid fullscreen option '" + arg + "'");
						usage();
						return;
					}
				}
				break;
			case 'g':
				arg = g.getOptarg();
				int cut = arg.indexOf("x", 0);
				if (cut == -1) {
					LOGGER.fatal("properJavaRDP: Invalid geometry: " + arg);
					usage();
					return;
				}
				options.width = Integer.parseInt(arg.substring(0, cut)) & ~3;
				options.height = Integer.parseInt(arg.substring(cut + 1));
				break;
			case 'k':
				arg = g.getOptarg();
				// options.keylayout = KeyLayout.strToCode(arg);
				if (options.keylayout == -1) {
					LOGGER.fatal("properJavaRDP: Invalid key layout: "
							+ arg);
					usage();
					return;
				}
				break;
			case 'l':
				throw new UnsupportedOperationException("Use a custom log4j configuration file");
			case 'n':
				options.hostname = g.getOptarg();
				break;
			case 'p':
				options.password = g.getOptarg();
				logonflags |= Rdp.RDP_LOGON_AUTO;
				break;
			case 's':
				options.command = g.getOptarg();
				break;
			case 'u':
				options.username = g.getOptarg();
				break;
			case 't':
				arg = g.getOptarg();
				try {
					options.port = Integer.parseInt(arg);
				} catch (NumberFormatException nex) {
					LOGGER.fatal("properJavaRDP: Invalid port number: " + arg);
					usage();
					return;
				}
				break;
			case 'T':
				options.windowTitle = g.getOptarg().replace('_', ' ');
				break;
			case 'r':
				options.licence_path = g.getOptarg();
				break;

			case '?':
			default:
				usage();
				return;

			}
		}

		if (fKdeHack) {
			options.height -= 46;
		}

		String server;

		if (g.getOptind() < args.length) {
			int colonat = args[args.length - 1].indexOf(":", 0);
			if (colonat == -1) {
				server = args[args.length - 1];
			} else {
				server = args[args.length - 1].substring(0, colonat);
				options.port = Integer.parseInt(args[args.length - 1]
						.substring(colonat + 1));
			}
		} else {
			LOGGER.fatal("properJavaRDP: A server name is required!");
			usage();
			return;
		}

		// Now do the startup...

		LOGGER.info("properJavaRDP version " + Version.version);

		if (args.length == 0) {
			usage();
			return;
		}

		String java = System.getProperty("java.specification.version");
		LOGGER.info("Java version is " + java);

		String os = System.getProperty("os.name");
		String osvers = System.getProperty("os.version");

		if (os.equals("Windows 2000") || os.equals("Windows XP")) {
			options.built_in_licence = true;
		}

		LOGGER.info("Operating System is " + os + " version " + osvers);

		if (os.startsWith("Linux")) {
			options.os = Options.OS.LINUX;
		} else if (os.startsWith("Windows")) {
			options.os = Options.OS.WINDOWS;
		} else if (os.startsWith("Mac")) {
			options.os = Options.OS.MAC;
		}

		if (options.os == Options.OS.MAC) {
			options.caps_sends_up_and_down = false;
		}

		Rdp RdpLayer;
		RdesktopFrame window = new RdesktopFrame(options);

		// Configure a keyboard layout
		KeyCode_FileBased keyMap;
		try {
			String filename = keyMapPath + mapFile;
			try (InputStream istr = Rdesktop.class.getResourceAsStream("/" + filename)) {
				if (istr == null) {
					LOGGER.debug("Loading keymap from filename: " + filename);
					keyMap = new KeyCode_FileBased(options, filename);
				} else {
					LOGGER.debug("Loading keymap from InputStream: " + "/" + filename);
					keyMap = new KeyCode_FileBased(options, istr);
				}
			}
			options.keylayout = keyMap.getMapCode();
		} catch (Exception kmEx) {
			LOGGER.warn("Unexpected keymap exception: ", kmEx);
			String[] msg = { (kmEx.getClass() + ": " + kmEx.getMessage()) };
			window.showErrorDialog(msg);
			Rdesktop.exit(0, null, null, true);
			return;
		}

		LOGGER.debug("Registering keyboard...");
		window.registerKeyboard(keyMap);

		LOGGER.debug("Initialising RDP layer...");
		RdpLayer = new Rdp(options);
		LOGGER.debug("Registering drawing surface...");
		RdpLayer.registerDrawingSurface(window);
		LOGGER.debug("Registering comms layer...");
		window.registerCommLayer(RdpLayer);
		LOGGER.info("Connecting to " + server + ":" + options.port
				+ " ...");

		if (server.equalsIgnoreCase("localhost")) {
			server = "127.0.0.1";
		}

		// Attempt to connect to server on port options.port
		try {
			RdpLayer.connect(options.username, InetAddress
					.getByName(server), logonflags, options.domain,
					options.password, options.command,
					options.directory);

			LOGGER.info("Connection successful");
			// now show window after licence negotiation
			DisconnectInfo info = RdpLayer.mainLoop();

			LOGGER.info("Disconnect: " + info);

			if (info.wasCleanDisconnect()) {
				/* clean disconnect */
				Rdesktop.exit(0, RdpLayer, window, true);
				// return 0;
			} else {
				if (info.getReason() == Reason.RPC_INITIATED_DISCONNECT
						|| info.getReason() == Reason.RPC_INITIATED_DISCONNECT) {
					/*
					 * not so clean disconnect, but nothing to worry
					 * about
					 */
					Rdesktop.exit(0, RdpLayer, window, true);
					// return 0;
				} else {
					String reason = info.toString();
					String msg[] = { "Connection terminated",
							reason };
					window.showErrorDialog(msg);
					LOGGER.warn("Connection terminated: " + reason);
					Rdesktop.exit(0, RdpLayer, window, true);
				}

			}

			if (RdpLayer.getState() != InitState.READY_TO_SEND) {
				// maybe the licence server was having a comms
				// problem, retry?
				String msg1 = "The terminal server disconnected before licence negotiation completed.";
				String msg2 = "Possible cause: terminal server could not issue a licence.";
				String[] msg = { msg1, msg2 };
				LOGGER.warn(msg1);
				LOGGER.warn(msg2);
				window.showErrorDialog(msg);
			}
		} catch (ConnectionException e) {
			LOGGER.warn("Connection exception", e);
			String msg[] = { "Connection Exception", e.getMessage() };
			window.showErrorDialog(msg);
			Rdesktop.exit(0, RdpLayer, window, true);
		} catch (UnknownHostException e) {
			LOGGER.warn("Unknown host exception", e);
			error(e, RdpLayer, window, true);
		} catch (SocketException s) {
			LOGGER.warn("Socket exception", s);
			if (RdpLayer.isConnected()) {
				LOGGER.fatal(s.getClass().getName() + " "
						+ s.getMessage());
				error(s, RdpLayer, window, true);
				Rdesktop.exit(0, RdpLayer, window, true);
			}
		} catch (RdesktopException e) {
			String msg1 = e.getClass().getName();
			String msg2 = e.getMessage();
			LOGGER.fatal(msg1 + ": " + msg2, e);

			if (RdpLayer.getState() != InitState.READY_TO_SEND) {
				// maybe the licence server was having a comms
				// problem, retry?
				String msg[] = {
						"The terminal server reset connection before licence negotiation completed.",
						"Possible cause: terminal server could not connect to licence server." };
				LOGGER.warn(msg1);
				LOGGER.warn(msg2);
				window.showErrorDialog(msg);
				Rdesktop.exit(0, RdpLayer, window, true);
			} else {
				String msg[] = { e.getMessage() };
				window.showErrorDialog(msg);
				Rdesktop.exit(0, RdpLayer, window, true);
			}
		} catch (Exception e) {
			LOGGER.warn("Other unhandled exception: " + e.getClass().getName() + " " + e.getMessage(), e);
			error(e, RdpLayer, window, true);
		}
		Rdesktop.exit(0, RdpLayer, window, true);
	}

	/**
	 * Disconnects from the server connected to through rdp and destroys the
	 * RdesktopFrame window.
	 * <p>
	 * Exits the application iff sysexit == true, providing return value n to
	 * the operating system.
	 *
	 * @param n
	 * @param rdp
	 * @param window
	 * @param sysexit
	 */
	@Deprecated
	public static void exit(int n, Rdp rdp, RdesktopFrame window,
			boolean sysexit) {

		if (rdp != null && rdp.isConnected()) {
			LOGGER.info("Disconnecting ...");
			rdp.disconnect();
			LOGGER.info("Disconnected");
		}
		if (window != null) {
			window.setVisible(false);
			window.dispose();
		}

		System.gc();

		if (sysexit) {
			if (!rdp.options/* XXX THIS IS NOT GOOD */.noSystemExit) {
				System.exit(n);
			}
		}
	}

	/**
	 * Displays an error dialog via the RdesktopFrame window containing the
	 * customised message emsg, and reports this through the logging system.
	 * <p>
	 * The application then exits iff sysexit == true
	 *
	 * @param emsg
	 * @param RdpLayer
	 * @param window
	 * @param sysexit
	 */
	@Deprecated
	public static void customError(String emsg, Rdp RdpLayer,
			RdesktopFrame window, boolean sysexit) {
		LOGGER.fatal(emsg);
		String[] msg = { emsg };
		window.showErrorDialog(msg);
		Rdesktop.exit(0, RdpLayer, window, true);
	}

	/**
	 * Displays details of the Exception e in an error dialog via the
	 * RdesktopFrame window and reports this through the logger, then prints a
	 * stack trace.
	 * <p>
	 * The application then exits iff sysexit == true
	 *
	 * @param e
	 * @param RdpLayer
	 * @param window
	 * @param sysexit
	 */
	@Deprecated
	public static void error(Exception e, Rdp RdpLayer, RdesktopFrame window,
			boolean sysexit) {
		try {

			String msg1 = e.getClass().getName();
			String msg2 = e.getMessage();

			LOGGER.fatal(msg1 + ": " + msg2, e);

			String[] msg = { msg1, msg2 };
			window.showErrorDialog(msg);
		} catch (Exception ex) {
			LOGGER.warn("Exception in Rdesktop.error: "
					+ ex.getClass().getName() + ": " + ex.getMessage(), ex);
		}

		Rdesktop.exit(0, RdpLayer, window, sysexit);
	}
}

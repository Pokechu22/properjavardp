/* Rdesktop.java
 * Component: ProperJavaRDP
 * 
 * Revision: $Revision$
 * Author: $Author$
 * Date: $Date$
 *
 * Copyright (c) 2005 Propero Limited
 *
 * Purpose: Main class, launches session
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
import net.propero.rdp.keymapping.KeyCode_FileBased;
import net.propero.rdp.rdp5.Rdp5;
import net.propero.rdp.rdp5.VChannels;
import net.propero.rdp.rdp5.cliprdr.ClipChannel;
import net.propero.rdp.tools.SendEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Rdesktop {

	static Logger logger = LogManager.getLogger("net.propero.rdp");

	@Deprecated
	public static boolean keep_running;

	@Deprecated
	public static boolean loggedon;

	@Deprecated
	public static boolean readytosend;

	@Deprecated
	public static boolean showTools;

	public static final String keyMapPath = "keymaps/";

	@Deprecated
	public static String mapFile = "en-us";

	@Deprecated
	public static String keyMapLocation = "";

	@Deprecated
	public static SendEvent toolFrame = null;

	/**
	 * Outputs version and usage information via System.err
	 * 
	 */
	public static void usage() {
		System.err.println("properJavaRDP version " + Version.version);
		System.err
				.println("Usage: java net.propero.rdp.Rdesktop [options] server[:port]");
		System.err
				.println("	-b 							bandwidth saving (good for 56k modem, but higher latency");
		System.err.println("	-c DIR						working directory");
		System.err.println("	-d DOMAIN					logon domain");
		System.err
				.println("	-f[l]						full-screen mode [with Linux KDE optimization]");
		System.err.println("	-g WxH						desktop geometry");
		System.err
				.println("	-m MAPFILE					keyboard mapping file for terminal server");
		System.err
				.println("	-l LEVEL					logging level {DEBUG, INFO, WARN, ERROR, FATAL}");
		System.err.println("	-n HOSTNAME					client hostname");
		System.err.println("	-p PASSWORD					password");
		System.err.println("	-s SHELL					shell");
		System.err.println("	-t NUM						RDP port (default 3389)");
		System.err.println("	-T TITLE					window title");
		System.err.println("	-u USERNAME					user name");
		System.err.println("	-o BPP						bits-per-pixel for display");
		System.err
				.println("    -r path                     path to load licence from (requests and saves licence from server if not found)");
		System.err
				.println("    --save_licence              request and save licence from server");
		System.err
				.println("    --load_licence              load licence from file");
		System.err
				.println("    --console                   connect to console");
		System.err
				.println("	--debug_key 				show scancodes sent for each keypress etc");
		System.err.println("	--debug_hex 				show bytes sent and received");
		System.err.println("	--no_remap_hash 			disable hash remapping");
		System.err.println("	--quiet_alt 				enable quiet alt fix");
		System.err
				.println("	--no_encryption				disable encryption from client to server");
		System.err.println("	--use_rdp4					use RDP version 4");
		// System.err.println(" --enable_menu enable menu bar");
		System.err
				.println("	--log4j_config=FILE			use FILE for log4j configuration");
		System.err
				.println("Example: java net.propero.rdp.Rdesktop -g 800x600 -l WARN m52.propero.int");
		Rdesktop.exit(0, null, null, true);
	}

	/**
	 * 
	 * @param args
	 * @throws OrderException
	 * @throws RdesktopException
	 */
	public static void main(String[] args) throws RdesktopException {

		// Ensure that static variables are properly initialised
		keep_running = true;
		loggedon = false;
		readytosend = false;
		showTools = false;
		mapFile = "en-gb";
		keyMapLocation = "";
		toolFrame = null;

		// Attempt to run a native RDP Client

		Options options = new Options();

		RDPClientChooser Chooser = new RDPClientChooser(options);

		if (Chooser.RunNativeRDPClient(args)) {
			if (!options.noSystemExit)
				System.exit(0);
		}

		// Failed to run native client, drop back to Java client instead.

		// parse arguments

		int logonflags = Rdp.RDP_LOGON_NORMAL;

		boolean fKdeHack = false;
		int c;
		String arg;
		StringBuffer sb = new StringBuffer();
		LongOpt[] alo = new LongOpt[15];
		alo[0] = new LongOpt("debug_key", LongOpt.NO_ARGUMENT, null, 0);
		alo[1] = new LongOpt("debug_hex", LongOpt.NO_ARGUMENT, null, 0);
		alo[2] = new LongOpt("no_paste_hack", LongOpt.NO_ARGUMENT, null, 0);
		alo[3] = new LongOpt("log4j_config", LongOpt.REQUIRED_ARGUMENT, sb, 0);
		alo[4] = new LongOpt("packet_tools", LongOpt.NO_ARGUMENT, null, 0);
		alo[5] = new LongOpt("quiet_alt", LongOpt.NO_ARGUMENT, sb, 0);
		alo[6] = new LongOpt("no_remap_hash", LongOpt.NO_ARGUMENT, null, 0);
		alo[7] = new LongOpt("no_encryption", LongOpt.NO_ARGUMENT, null, 0);
		alo[8] = new LongOpt("use_rdp4", LongOpt.NO_ARGUMENT, null, 0);
		alo[9] = new LongOpt("use_ssl", LongOpt.NO_ARGUMENT, null, 0);
		alo[10] = new LongOpt("enable_menu", LongOpt.NO_ARGUMENT, null, 0);
		alo[11] = new LongOpt("console", LongOpt.NO_ARGUMENT, null, 0);
		alo[12] = new LongOpt("load_licence", LongOpt.NO_ARGUMENT, null, 0);
		alo[13] = new LongOpt("save_licence", LongOpt.NO_ARGUMENT, null, 0);
		alo[14] = new LongOpt("persistent_caching", LongOpt.NO_ARGUMENT, null,
				0);

		String progname = "properJavaRDP";

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
					showTools = true;
					break;
				case 5:
					options.altkey_quiet = true;
					break;
				case 6:
					options.remap_hash = false;
					break;
				case 7:
					options.packet_encryption = false;
					break;
				case 8:
					options.use_rdp5 = false;
					// options.server_bpp = 8;
					options.set_bpp(8);
					break;
				case 9:
					options.use_ssl = true;
					break;
				case 10:
					options.enable_menu = true;
					break;
				case 11:
					options.console_session = true;
					break;
				case 12:
					options.load_licence = true;
					break;
				case 13:
					options.save_licence = true;
					break;
				case 14:
					options.persistent_bitmap_caching = true;
					break;
				default:
					usage();
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
					if (arg.charAt(0) == 'l')
						fKdeHack = true;
					else {
						System.err.println(progname
								+ ": Invalid fullscreen option '" + arg + "'");
						usage();
					}
				}
				break;
			case 'g':
				arg = g.getOptarg();
				int cut = arg.indexOf("x", 0);
				if (cut == -1) {
					System.err.println(progname + ": Invalid geometry: " + arg);
					usage();
				}
				options.width = Integer.parseInt(arg.substring(0, cut)) & ~3;
				options.height = Integer.parseInt(arg.substring(cut + 1));
				break;
			case 'k':
				arg = g.getOptarg();
				// options.keylayout = KeyLayout.strToCode(arg);
				if (options.keylayout == -1) {
					System.err.println(progname + ": Invalid key layout: "
							+ arg);
					usage();
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
					System.err.println(progname + ": Invalid port number: "
							+ arg);
					usage();
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
				break;

			}
		}

		if (fKdeHack) {
			options.height -= 46;
		}

		String server = null;

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
			System.err.println(progname + ": A server name is required!");
			usage();
		}

		VChannels channels = new VChannels(options);

		ClipChannel clipChannel = new ClipChannel(options);

		// Initialise all RDP5 channels
		if (options.use_rdp5) {
			// TODO: implement all relevant channels
			if (options.map_clipboard)
				channels.register(clipChannel);
		}

		// Now do the startup...

		logger.info("properJavaRDP version " + Version.version);

		if (args.length == 0)
			usage();

		String java = System.getProperty("java.specification.version");
		logger.info("Java version is " + java);

		String os = System.getProperty("os.name");
		String osvers = System.getProperty("os.version");

		if (os.equals("Windows 2000") || os.equals("Windows XP"))
			options.built_in_licence = true;

		logger.info("Operating System is " + os + " version " + osvers);

		if (os.startsWith("Linux"))
			options.os = Options.OS.LINUX;
		else if (os.startsWith("Windows"))
			options.os = Options.OS.WINDOWS;
		else if (os.startsWith("Mac"))
			options.os = Options.OS.MAC;

		if (options.os == Options.OS.MAC)
			options.caps_sends_up_and_down = false;

		Rdp5 RdpLayer = null;
		RdesktopFrame window = new RdesktopFrame_Localised(options);
		window.setClip(clipChannel);

		// Configure a keyboard layout
		KeyCode_FileBased keyMap = null;
		try {
			// logger.info("looking for: " + "/" + keyMapPath + mapFile);
			InputStream istr = Rdesktop.class.getResourceAsStream("/"
					+ keyMapPath + mapFile);
			// logger.info("istr = " + istr);
			if (istr == null) {
				logger.debug("Loading keymap from filename");
				keyMap = new KeyCode_FileBased_Localised(options, keyMapPath + mapFile);
			} else {
				logger.debug("Loading keymap from InputStream");
				keyMap = new KeyCode_FileBased_Localised(options, istr);
			}
			if (istr != null)
				istr.close();
			options.keylayout = keyMap.getMapCode();
		} catch (Exception kmEx) {
			String[] msg = { (kmEx.getClass() + ": " + kmEx.getMessage()) };
			window.showErrorDialog(msg);
			kmEx.printStackTrace();
			Rdesktop.exit(0, null, null, true);
		}

		logger.debug("Registering keyboard...");
		if (keyMap != null)
			window.registerKeyboard(keyMap);

		logger.debug("keep_running = " + keep_running);
		while (keep_running) {
			logger.debug("Initialising RDP layer...");
			RdpLayer = new Rdp5(options, channels);
			clipChannel.setSecure(RdpLayer.SecureLayer);  // XXX this shouldn't be needed
			logger.debug("Registering drawing surface...");
			RdpLayer.registerDrawingSurface(window);
			logger.debug("Registering comms layer...");
			window.registerCommLayer(RdpLayer);
			loggedon = false;
			readytosend = false;
			logger
					.info("Connecting to " + server + ":" + options.port
							+ " ...");

			if (server.equalsIgnoreCase("localhost"))
				server = "127.0.0.1";

			if (RdpLayer != null) {
				// Attempt to connect to server on port options.port
				try {
					RdpLayer.connect(options.username, InetAddress
							.getByName(server), logonflags, options.domain,
							options.password, options.command,
							options.directory);

					// Remove to get rid of sendEvent tool
					if (showTools) {
						toolFrame = new SendEvent(RdpLayer);
						toolFrame.show();
					}
					// End

					if (keep_running) {

						/*
						 * By setting encryption to False here, we have an
						 * encrypted login packet but unencrypted transfer of
						 * other packets
						 */
						if (!options.packet_encryption)
							options.encryption = false;

						logger.info("Connection successful");
						// now show window after licence negotiation
						DisconnectInfo info = RdpLayer.mainLoop();

						logger.info("Disconnect: " + info);

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
								logger.warn("Connection terminated: " + reason);
								Rdesktop.exit(0, RdpLayer, window, true);
							}

						}

						keep_running = false; // exited main loop
						if (!readytosend) {
							// maybe the licence server was having a comms
							// problem, retry?
							String msg1 = "The terminal server disconnected before licence negotiation completed.";
							String msg2 = "Possible cause: terminal server could not issue a licence.";
							String[] msg = { msg1, msg2 };
							logger.warn(msg1);
							logger.warn(msg2);
							window.showErrorDialog(msg);
						}
					} // closing bracket to if(running)

					// Remove to get rid of tool window
					if (showTools)
						toolFrame.dispose();
					// End

				} catch (ConnectionException e) {
					String msg[] = { "Connection Exception", e.getMessage() };
					e.printStackTrace();
					window.showErrorDialog(msg);
					Rdesktop.exit(0, RdpLayer, window, true);
				} catch (UnknownHostException e) {
					error(e, RdpLayer, window, true);
				} catch (SocketException s) {
					if (RdpLayer.isConnected()) {
						logger.fatal(s.getClass().getName() + " "
								+ s.getMessage());
						s.printStackTrace();
						error(s, RdpLayer, window, true);
						Rdesktop.exit(0, RdpLayer, window, true);
					}
				} catch (RdesktopException e) {
					String msg1 = e.getClass().getName();
					String msg2 = e.getMessage();
					logger.fatal(msg1 + ": " + msg2);

					System.out.flush();
					e.printStackTrace(System.err);

					if (!readytosend) {
						// maybe the licence server was having a comms
						// problem, retry?
						String msg[] = {
								"The terminal server reset connection before licence negotiation completed.",
								"Possible cause: terminal server could not connect to licence server.",
								"Retry?" };
						boolean retry = window.showYesNoErrorDialog(msg);
						if (!retry) {
							logger.info("Selected not to retry.");
							Rdesktop.exit(0, RdpLayer, window, true);
						} else {
							if (RdpLayer != null && RdpLayer.isConnected()) {
								logger.info("Disconnecting ...");
								RdpLayer.disconnect();
								logger.info("Disconnected");
							}
							logger.info("Retrying connection...");
							keep_running = true; // retry
							continue;
						}
					} else {
						String msg[] = { e.getMessage() };
						window.showErrorDialog(msg);
						Rdesktop.exit(0, RdpLayer, window, true);
					}
				} catch (Exception e) {
					logger.warn(e.getClass().getName() + " " + e.getMessage());
					e.printStackTrace();
					error(e, RdpLayer, window, true);
				}
			} else { // closing bracket to if(!rdp==null)
				logger
						.fatal("The communications layer could not be initiated!");
			}
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
		keep_running = false;

		// Remove to get rid of tool window
		if ((showTools) && (toolFrame != null))
			toolFrame.dispose();
		// End

		if (rdp != null && rdp.isConnected()) {
			logger.info("Disconnecting ...");
			rdp.disconnect();
			logger.info("Disconnected");
		}
		if (window != null) {
			window.setVisible(false);
			window.dispose();
		}

		System.gc();

		if (sysexit) {
			if (!rdp.options/* XXX THIS IS NOT GOOD */.noSystemExit)
				System.exit(n);
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
		logger.fatal(emsg);
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

			logger.fatal(msg1 + ": " + msg2);

			String[] msg = { msg1, msg2 };
			window.showErrorDialog(msg);

			e.printStackTrace(System.err);
		} catch (Exception ex) {
			logger.warn("Exception in Rdesktop.error: "
					+ ex.getClass().getName() + ": " + ex.getMessage(), ex);
		}

		Rdesktop.exit(0, RdpLayer, window, sysexit);
	}
}

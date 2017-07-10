/* Rdp.java
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

import java.awt.Cursor;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.IndexColorModel;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

import javax.annotation.Nullable;

import net.propero.rdp.Input.InputCapsetFlag;
import net.propero.rdp.Input.InputType;
import net.propero.rdp.Orders.PrimaryOrder;
import net.propero.rdp.api.RdesktopCallback;
import net.propero.rdp.api.InitState;
import net.propero.rdp.rdp5.Rdp5;
import net.propero.rdp.rdp5.VChannels;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * Rdp layer of communication
 */
public class Rdp {

	public static final boolean SAVE_DESKTOP = true; // Configuration?

	public static int RDP5_DISABLE_NOTHING = 0x00;

	public static int RDP5_NO_WALLPAPER = 0x01;

	public static int RDP5_NO_FULLWINDOWDRAG = 0x02;

	public static int RDP5_NO_MENUANIMATIONS = 0x04;

	public static int RDP5_NO_THEMING = 0x08;

	public static int RDP5_NO_CURSOR_SHADOW = 0x20;

	/** disables cursor blinking */
	public static int RDP5_NO_CURSORSETTINGS = 0x40;

	private static final Logger LOGGER = LogManager.getLogger();

	/* constants for RDP Layer */
	public static final int RDP_LOGON_NORMAL = 0x33;

	public static final int RDP_LOGON_AUTO = 0x8;

	public static final int RDP_LOGON_BLOB = 0x100;

	// PDU Types
	private static final int RDP_PDU_DEMAND_ACTIVE = 1;

	private static final int RDP_PDU_CONFIRM_ACTIVE = 3;

	private static final int RDP_PDU_DEACTIVATE = 6;

	private static final int RDP_PDU_DATA = 7;

	// Data PDU Types
	private static final int RDP_DATA_PDU_UPDATE = 2;

	private static final int RDP_DATA_PDU_CONTROL = 20;

	private static final int RDP_DATA_PDU_POINTER = 27;

	private static final int RDP_DATA_PDU_INPUT = 28;

	private static final int RDP_DATA_PDU_SYNCHRONISE = 31;

	private static final int RDP_DATA_PDU_BELL = 34;

	private static final int RDP_DATA_PDU_LOGON = 38;

	private static final int RDP_DATA_PDU_FONT2 = 39;

	private static final int RDP_DATA_PDU_DISCONNECT = 47;

	// Control PDU types
	private static final int RDP_CTL_REQUEST_CONTROL = 1;

	private static final int RDP_CTL_GRANT_CONTROL = 2;

	private static final int RDP_CTL_DETACH = 3;

	private static final int RDP_CTL_COOPERATE = 4;

	// Update PDU Types
	private static final int RDP_UPDATE_ORDERS = 0;

	private static final int RDP_UPDATE_BITMAP = 1;

	private static final int RDP_UPDATE_PALETTE = 2;

	private static final int RDP_UPDATE_SYNCHRONIZE = 3;

	// Pointer PDU Types
	private static final int RDP_POINTER_SYSTEM = 1;

	private static final int RDP_POINTER_MOVE = 3;

	private static final int RDP_POINTER_COLOR = 6;

	private static final int RDP_POINTER_CACHED = 7;

	// System Pointer Types
	private static final int RDP_NULL_POINTER = 0;

	private static final int RDP_DEFAULT_POINTER = 0x7F00;

	/* RDP capabilities */

	private static final int OS_MAJOR_TYPE_UNIX = 4;

	private static final int OS_MINOR_TYPE_XSERVER = 7;

	private static final int ORDER_CAP_NEGOTIATE = 2;

	private static final int ORDER_CAP_NOSUPPORT = 4;

	private static enum Capset {
		GENERAL(0x01, 0x18),
		BITMAP(0x02, 0x1C),
		ORDER(0x03, 0x58),
		BITMAPCACHE(0x04, 0x28),
		CONTROL(0x05, 0x0C),

		ACTIVATION(0x07, 0x0C),
		POINTER(0x08, 0x08),
		SHARE(0x09, 0x08),
		COLORCACHE(0x0A, 0x08),

		SOUND(0x0C, 0x08),
		INPUT(0x0D, 0x58),
		FONT(0x0E, 0x08),
		BRUSH(0x0F),
		GLYPHCACHE(0x10, 0x34),
		OFFSCREENCACHE(0x11),
		BITMAPCACHE_HOSTSUPPORT(0x12),
		BITMAPCACHE_REV2(0x13, 0x28),
		VIRTUALCHANNEL(0x14),
		DRAWNINEGRIDCACHE(0x15),
		DRAWGDIPLUS(0x16),
		RAIL(0x17),
		WINDOW(0x18),
		COMPDESK(0x19),
		MULTIFRAGMENTUPDATE(0x1A),
		LARGE_POINTER(0x1B),
		SURFACE_COMMANDS(0x1C),
		BITMAP_CODECS(0x1D),
		FRAME_ACKNOWLEDGE(0x1E),
		;
		public final int id; // unsigned 16-bit: the ID
		private final int len; // unsigned 16-bit: the length in bytes (I'm assuming this is always that size)

		private Capset(final int id) {
			this(id, -1);
		}

		private Capset(final int id, final int len) {
			if (id < 0 || id > 0xFFFF) {
				throw new IllegalArgumentException("id must be an unsigned short; got " + id);
			}
			if (len < -1 || len > 0xFFFF) {
				throw new IllegalArgumentException("len must be an unsigned short or -1; got " + len);
			}
			this.id = id;
			this.len = len;
		}

		/**
		 * Gets the expected length of this capset, if known, including the 4-byte header.
		 * @throws UnsupportedOperationException when the capset length is not known.
		 */
		public int getLength() throws UnsupportedOperationException {
			if (len == -1) {
				throw new UnsupportedOperationException("Don't know the length of capset " + this);
			}
			return len;
		}

		@Override
		public String toString() {
			return super.toString() + " (id=0x" + Integer.toHexString(this.id) + ", len=" + (len != -1 ? "0x" + Integer.toHexString(this.len) : "?") + ")";
		}

		private static final Capset[] BY_ID;
		static {
			int largestID = 0;
			for (Capset capset : values()) {
				if (capset.id > largestID) {
					largestID = capset.id;
				}
			}
			BY_ID = new Capset[largestID + 1];
			for (Capset capset : values()) {
				if (BY_ID[capset.id] != null) {
					throw new AssertionError("Duplicate capsets with ID " + capset.id + ": tried to register " + capset + " over " + BY_ID[capset.id] + "!");
				}
				BY_ID[capset.id]= capset;
			}
		}

		/**
		 * Attempts to fetch a capset with the given id.
		 * @param id The ID to fetch.  Should be positive.
		 * @return The matching capset, or <code>null</code> if none match.
		 */
		public static Capset forId(int id) {
			if (id < 0) {
				throw new IllegalArgumentException("id must be positive");
			}
			if (id >= BY_ID.length) {
				return null;
			}
			return BY_ID[id]; // may be null
		}
	}

	private static final int BMPCACHE2_FLAG_PERSIST = (1 << 31);

	/* RDP bitmap cache (version 2) constants */
	public static final int BMPCACHE2_C0_CELLS = 0x78;

	public static final int BMPCACHE2_C1_CELLS = 0x78;

	public static final int BMPCACHE2_C2_CELLS = 0x150;

	public static final int BMPCACHE2_NUM_PSTCELLS = 0x9f6;

	private static final int RDP5_FLAG = 0x0030;

	/** MSTSC encoded as 7 byte US-Ascii */
	private static final byte[] RDP_SOURCE = { (byte) 0x4D, (byte) 0x53,
		(byte) 0x54, (byte) 0x53, (byte) 0x43, (byte) 0x00 }; // string

	@Nullable
	protected final Secure SecureLayer;

	private final OrderSurface surface;
	private RdesktopCallback callback = null;

	protected Orders orders = null;

	private Cache cache = null;

	private int next_packet = 0;

	private int rdp_shareid = 0;

	private boolean connected = false;

	private RdpPacket stream = null;

	protected final Options options;

	private InitState state;

	public Rdp(Options options) {
		this.options = options;
		this.surface = new OrderSurface(options, options.width, options.height);
		this.SecureLayer = null;
	}

	/**
	 * Gets the current state in the initialization process.
	 */
	public InitState getState() {
		return state;
	}

	/**
	 * Process a general capability set
	 *
	 * @param data
	 *            Packet containing capability set data at current read position
	 */
	private void processGeneralCaps(RdpPacket data) throws RdesktopException {
		int pad2octetsB; /* rdp5 flags? */

		data.incrementPosition(10); // in_uint8s(s, 10);
		pad2octetsB = data.getLittleEndian16(); // in_uint16_le(s, pad2octetsB);

		if (pad2octetsB != 0) {
			options.use_rdp5 = false;
		}
	}

	/**
	 * Process a bitmap capability set
	 *
	 * @param data
	 *            Packet containing capability set data at current read position
	 */
	private void processBitmapCaps(RdpPacket data) throws RdesktopException {
		int width, height, bpp;

		bpp = data.getLittleEndian16(); // in_uint16_le(s, bpp);
		data.incrementPosition(6); // in_uint8s(s, 6);

		width = data.getLittleEndian16(); // in_uint16_le(s, width);
		height = data.getLittleEndian16(); // in_uint16_le(s, height);

		LOGGER.debug("setting desktop size and bpp to: " + width + "x" + height
				+ "x" + bpp);

		/*
		 * The server may limit bpp and change the size of the desktop (for
		 * example when shadowing another session).
		 */
		if (options.server_bpp != bpp) {
			LOGGER.warn("colour depth changed from " + options.server_bpp
					+ " to " + bpp);
			options.server_bpp = bpp;
		}
		if (options.width != width || options.height != height) {
			String msg = "screen size changed from " + options.width + "x"
					+ options.height + " to " + width + "x" + height;
			LOGGER.warn(msg);
			options.width = width;
			options.height = height;
			this.callback.sizeChanged(width, height);
			this.surface.sizeChanged();
		}
	}

	/**
	 * Process an input capability set
	 *
	 * @param data
	 *            Packet containing capability set data at current read position
	 */
	private void processInputCaps(RdpPacket data) throws RdesktopException {
		int flags = data.getLittleEndian16();
		options.supportedInputFlags.clear();
		for (InputCapsetFlag flag : InputCapsetFlag.values()) {
			if ((flags & flag.flag) != 0) {
				options.supportedInputFlags.add(flag);
			}
		}
		if (!options.supportedInputFlags.contains(InputCapsetFlag.SCANCODES)) {
			throw new RdesktopException("Server doesn't support scancodes: " + options.supportedInputFlags);
		}

		data.getLittleEndian16();  // Pad
		data.getLittleEndian32();  // Keyboard layout - ignored for serverer
		data.getLittleEndian32();  // Keyboard type - ignored for server
		data.getLittleEndian32();  // Keyboard subtype - ignored for server
		data.getLittleEndian32();  // Number of function keys - ignored for server
		data.incrementPosition(64);  // IME layout; also ignored for the server
	}

	/**
	 * Process server capabilities
	 *
	 * @param data
	 *            Packet containing capability set data at current read position
	 */
	private void processServerCaps(RdpPacket data, int length) throws RdesktopException {
		int n;
		int next, start;
		int ncapsets, capset_type, capset_length;

		start = data.getPosition();

		ncapsets = data.getLittleEndian16(); // in_uint16_le(s, ncapsets);
		data.incrementPosition(2); // in_uint8s(s, 2); /* pad */

		for (n = 0; n < ncapsets; n++) {
			if (data.getPosition() > start + length) {
				return;
			}

			capset_type = data.getLittleEndian16(); // in_uint16_le(s,
			// capset_type);
			capset_length = data.getLittleEndian16(); // in_uint16_le(s,
			// capset_length);

			Capset capset = Capset.forId(capset_type);

			next = data.getPosition() + capset_length - 4;

			if (capset == null) {
				LOGGER.warn("Unknown server capset " + capset_type + " (sent len 0x" + Integer.toHexString(capset_length) + ")");
			} else {
				switch (capset) {
				case GENERAL:
					processGeneralCaps(data);
					break;

				case BITMAP:
					processBitmapCaps(data);
					break;

				case INPUT:
					processInputCaps(data);
					break;

				default:
					LOGGER.warn("Unhandled server capset " + capset + " (sent len 0x" + Integer.toHexString(capset_length) + ")");
					break;
				}
			}

			data.setPosition(next);
		}
	}

	/**
	 * Process a disconnect PDU
	 *
	 * @param data
	 *            Packet containing disconnect PDU at current read position
	 * @return Code specifying the reason for disconnection
	 */
	protected int processDisconnectPdu(RdpPacket data) {
		LOGGER.debug("Received disconnect PDU");
		return data.getLittleEndian32();
	}

	/**
	 * Initialise RDP comms layer, and register virtual channels
	 *
	 * @param channels
	 *            Virtual channels to be used in connection
	 */
	public Rdp(Options options, VChannels channels) {
		this.options = options;
		this.SecureLayer = new Secure(channels, options, (Rdp5) this); // XXX Uuh, cast to self in constructor.  Not good.
		this.surface = new OrderSurface(options, options.width, options.height);
		this.orders = new Orders(options);
		this.cache = new Cache(options);
		orders.registerCache(cache);
	}

	/**
	 * Initialise a packet for sending data on the RDP layer
	 *
	 * @param size
	 *            Size of RDP data
	 * @return Packet initialised for RDP
	 * @throws RdesktopException
	 */
	private RdpPacket initData(int size) throws RdesktopException {
		RdpPacket buffer = null;

		buffer = SecureLayer.init(Secure.SEC_ENCRYPT, size + 18);
		buffer.pushLayer(RdpPacket.RDP_HEADER, 18);
		// buffer.setHeader(RdpPacket_Localised.RDP_HEADER);
		// buffer.incrementPosition(18);
		// buffer.setStart(buffer.getPosition());
		return buffer;
	}

	/**
	 * Send a packet on the RDP layer
	 *
	 * @param data
	 *            Packet to send
	 * @param data_pdu_type
	 *            Type of data
	 * @throws RdesktopException
	 * @throws IOException
	 */
	private void sendData(RdpPacket data, int data_pdu_type)
			throws RdesktopException, IOException {

		synchronized (this.SecureLayer) {
			int length;

			data.setPosition(data.getHeader(RdpPacket.RDP_HEADER));
			length = data.getEnd() - data.getPosition();

			data.setLittleEndian16(length);
			data.setLittleEndian16(RDP_PDU_DATA | 0x10);
			data.setLittleEndian16(SecureLayer.getUserID() + 1001);

			data.setLittleEndian32(this.rdp_shareid);
			data.set8(0); // pad
			data.set8(1); // stream id
			data.setLittleEndian16(length - 14);
			data.set8(data_pdu_type);
			data.set8(0); // compression type
			data.setLittleEndian16(0); // compression length

			SecureLayer.send(data, Secure.SEC_ENCRYPT);
		}
	}

	/**
	 * Receive a packet from the RDP layer
	 *
	 * @param type
	 *            Type of PDU received, stored in type[0]
	 * @return Packet received from RDP layer
	 * @throws IOException
	 * @throws RdesktopException
	 * @throws OrderException
	 */
	private RdpPacket receive(int[] type) throws IOException,
	RdesktopException, OrderException {
		int length = 0;

		if ((this.stream == null) || (this.next_packet >= this.stream.getEnd())) {
			this.stream = SecureLayer.receive();
			if (stream == null) {
				return null;
			}
			this.next_packet = this.stream.getPosition();
		} else {
			this.stream.setPosition(this.next_packet);
		}
		length = this.stream.getLittleEndian16();

		/* 32k packets are really 8, keepalive fix - rdesktop 1.2.0 */
		if (length == 0x8000) {
			LOGGER.warn("32k packet keepalive fix");
			next_packet += 8;
			type[0] = 0;
			return stream;
		}
		type[0] = this.stream.getLittleEndian16() & 0xf;
		if (stream.getPosition() != stream.getEnd()) {
			stream.incrementPosition(2);
		}

		this.next_packet += length;
		return stream;
	}

	/**
	 * Connect to a server
	 *
	 * @param username
	 *            Username for log on
	 * @param server
	 *            Server to connect to
	 * @param flags
	 *            Flags defining logon type
	 * @param domain
	 *            Domain for log on
	 * @param password
	 *            Password for log on
	 * @param command
	 *            Alternative shell for session
	 * @param directory
	 *            Initial working directory for connection
	 * @throws ConnectionException
	 */
	public void connect(String username, InetAddress server, int flags,
			String domain, String password, String command, String directory)
					throws ConnectionException {
		try {
			SecureLayer.connect(server);
			this.connected = true;
			this.sendLogonInfo(flags, domain, username, password, command,
					directory);
		}
		// Handle an unresolvable hostname
		catch (UnknownHostException e) {
			throw new ConnectionException("Could not resolve host name: "
					+ server, e);
		}
		// Handle a refused connection
		catch (ConnectException e) {
			throw new ConnectionException(
					"Connection refused when trying to connect to " + server
					+ " on port " + options.port, e);
		}
		// Handle a timeout on connecting
		catch (NoRouteToHostException e) {
			throw new ConnectionException(
					"Connection timed out when attempting to connect to "
							+ server, e);
		} catch (IOException e) {
			throw new ConnectionException("Connection Failed", e);
		} catch (RdesktopException e) {
			throw new ConnectionException(e.getMessage(), e);
		} catch (OrderException e) {
			throw new ConnectionException(e.getMessage(), e);
		}

	}

	/**
	 * Disconnect from an RDP session
	 */
	public void disconnect() {
		this.connected = false;
		SecureLayer.disconnect();
	}

	/**
	 * Retrieve status of connection
	 *
	 * @return True if connection to RDP session
	 */
	public boolean isConnected() {
		return this.connected;
	}

	/**
	 * RDP receive loop
	 *
	 * @return Info about the disconnection
	 * @throws IOException
	 * @throws RdesktopException
	 * @throws OrderException
	 */
	public DisconnectInfo mainLoop()
			throws IOException, RdesktopException, OrderException {
		int[] type = new int[1];

		RdpPacket data = null;

		boolean cleanDisconnect = false;

		while (true) {
			try {
				data = this.receive(type);
				if (data == null) {
					return new DisconnectInfo(false, "No data?");
				}
			} catch (EOFException e) {
				LOGGER.warn("Unexpected EOF", e);
				return new DisconnectInfo(false, "EOF?");
			}

			switch (type[0]) {

			case (Rdp.RDP_PDU_DEMAND_ACTIVE):
				LOGGER.debug("Rdp.RDP_PDU_DEMAND_ACTIVE");
				// get this after licence negotiation, just before the 1st
				// order...
				ThreadContext.push("processDemandActive");
				this.processDemandActive(data);
				// can use this to trigger things that have to be done before
				// 1st order
				LOGGER.debug("ready to send (got past licence negotiation)");
				state = InitState.READY_TO_SEND;
				callback.stateChanged(state);
				ThreadContext.pop();
				cleanDisconnect = false;
			break;

			case (Rdp.RDP_PDU_DEACTIVATE):
				// get this on log off
				cleanDisconnect = true;
			this.stream = null; // ty this fix
			break;

			case (Rdp.RDP_PDU_DATA):
				LOGGER.debug("Rdp.RDP_PDU_DATA");
			// all the others should be this
			ThreadContext.push("processData");

			Integer result = this.processData(data);
			if (result != null) {
				// Received a disconnect PDU; exit
				return new DisconnectInfo(cleanDisconnect, result.intValue());
			}
			ThreadContext.pop();
			break;

			case 0:
				break; // 32K keep alive fix, see receive() - rdesktop 1.2.0.

			default:
				throw new RdesktopException("Unimplemented type in main loop :"
						+ type[0]);
			}
		}
	}

	/**
	 * Send user logon details to the server
	 *
	 * @param flags
	 *            Set of flags defining logon type
	 * @param domain
	 *            Domain for logon
	 * @param username
	 *            Username for logon
	 * @param password
	 *            Password for logon
	 * @param command
	 *            Alternative shell for session
	 * @param directory
	 *            Starting working directory for session
	 * @throws RdesktopException
	 * @throws IOException
	 */
	private void sendLogonInfo(int flags, String domain, String username,
			String password, String command, String directory)
					throws RdesktopException, IOException {

		int len_ip = 2 * "127.0.0.1".length();
		int len_dll = 2 * "C:\\WINNT\\System32\\mstscax.dll".length();
		int packetlen = 0;

		int sec_flags = (Secure.SEC_LOGON_INFO | Secure.SEC_ENCRYPT);
		int domainlen = 2 * domain.length();
		int userlen = 2 * username.length();
		int passlen = 2 * password.length();
		int commandlen = 2 * command.length();
		int dirlen = 2 * directory.length();

		RdpPacket data;

		if (!options.use_rdp5 || 1 == options.server_rdp_version) {
			LOGGER.debug("Sending RDP4-style Logon packet");

			data = SecureLayer.init(sec_flags, 18 + domainlen + userlen
					+ passlen + commandlen + dirlen + 10);

			data.setLittleEndian32(0);
			data.setLittleEndian32(flags);
			data.setLittleEndian16(domainlen);
			data.setLittleEndian16(userlen);
			data.setLittleEndian16(passlen);
			data.setLittleEndian16(commandlen);
			data.setLittleEndian16(dirlen);
			data.outUnicodeString(domain, domainlen);
			data.outUnicodeString(username, userlen);
			data.outUnicodeString(password, passlen);
			data.outUnicodeString(command, commandlen);
			data.outUnicodeString(directory, dirlen);

		} else {
			flags |= RDP_LOGON_BLOB;
			LOGGER.debug("Sending RDP5-style Logon packet");
			packetlen = 4
					+ // Unknown uint32
					4
					+ // flags
					2
					+ // len_domain
					2
					+ // len_user
					((flags & RDP_LOGON_AUTO) != 0 ? 2 : 0)
					+ // len_password
					((flags & RDP_LOGON_BLOB) != 0 ? 2 : 0)
					+ // Length of BLOB
					2
					+ // len_program
					2
					+ // len_directory
					(0 < domainlen ? domainlen + 2 : 2)
					+ // domain
					userlen
					+ ((flags & RDP_LOGON_AUTO) != 0 ? passlen : 0)
					+ 0
					+ // We have no 512 byte BLOB. Perhaps we must?
					((flags & RDP_LOGON_BLOB) != 0
					&& (flags & RDP_LOGON_AUTO) == 0 ? 2 : 0)
					+ (0 < commandlen ? commandlen + 2 : 2)
					+ (0 < dirlen ? dirlen + 2 : 2) + 2 + // Unknown (2)
					2 + // Client ip length
					len_ip + // Client ip
					2 + // DLL string length
					len_dll + // DLL string
					2 + // Unknown
					2 + // Unknown
					64 + // Time zone #0
					20 + // Unknown
					64 + // Time zone #1
					32 + 6; // Unknown

			data = SecureLayer.init(sec_flags, packetlen); // s =
			// sec_init(sec_flags,
			// packetlen);
			// logger.debug("Called sec_init with packetlen " + packetlen);

			data.setLittleEndian32(0); // out_uint32(s, 0); // Unknown
			data.setLittleEndian32(flags); // out_uint32_le(s, flags);
			data.setLittleEndian16(domainlen); // out_uint16_le(s, len_domain);
			data.setLittleEndian16(userlen); // out_uint16_le(s, len_user);
			if ((flags & RDP_LOGON_AUTO) != 0) {
				data.setLittleEndian16(passlen); // out_uint16_le(s,
				// len_password);
			}
			if ((flags & RDP_LOGON_BLOB) != 0
					&& ((flags & RDP_LOGON_AUTO) == 0)) {
				data.setLittleEndian16(0); // out_uint16_le(s, 0);
			}
			data.setLittleEndian16(commandlen); // out_uint16_le(s,
			// len_program);
			data.setLittleEndian16(dirlen); // out_uint16_le(s, len_directory);

			if (0 < domainlen) {
				data.outUnicodeString(domain, domainlen); // rdp_out_unistr(s,
				// domain,
				// len_domain);
			}
			else {
				data.setLittleEndian16(0); // out_uint16_le(s, 0);
			}

			data.outUnicodeString(username, userlen); // rdp_out_unistr(s,
			// user, len_user);
			if ((flags & RDP_LOGON_AUTO) != 0) {
				data.outUnicodeString(password, passlen); // rdp_out_unistr(s,
				// password,
				// len_password);
			}
			if ((flags & RDP_LOGON_BLOB) != 0 && (flags & RDP_LOGON_AUTO) == 0) {
				data.setLittleEndian16(0); // out_uint16_le(s, 0);
			}
			if (0 < commandlen) {
				data.outUnicodeString(command, commandlen); // rdp_out_unistr(s,
				// program,
				// len_program);
			} else {
				data.setLittleEndian16(0); // out_uint16_le(s, 0);
			}
			if (0 < dirlen) {
				data.outUnicodeString(directory, dirlen); // rdp_out_unistr(s,
				// directory,
				// len_directory);
			} else {
				data.setLittleEndian16(0); // out_uint16_le(s, 0);
			}
			data.setLittleEndian16(2); // out_uint16_le(s, 2);
			data.setLittleEndian16(len_ip + 2); // out_uint16_le(s, len_ip + 2);
			// // Length of client ip
			data.outUnicodeString("127.0.0.1", len_ip); // rdp_out_unistr(s,
			// "127.0.0.1",
			// len_ip);
			data.setLittleEndian16(len_dll + 2); // out_uint16_le(s, len_dll
			// + 2);
			data.outUnicodeString("C:\\WINNT\\System32\\mstscax.dll", len_dll); // rdp_out_unistr(s,
			// "C:\\WINNT\\System32\\mstscax.dll",
			// len_dll);
			data.setLittleEndian16(0xffc4); // out_uint16_le(s, 0xffc4);
			data.setLittleEndian16(0xffff); // out_uint16_le(s, 0xffff);
			data.outUnicodeString("GTB, normaltid", 2 * "GTB, normaltid"
					.length()); // rdp_out_unistr(s, "GTB, normaltid", 2 *
			// strlen("GTB, normaltid"));
			data.incrementPosition(62 - 2 * "GTB, normaltid".length()); // out_uint8s(s,
			// 62 -
			// 2 *
			// strlen("GTB,
			// normaltid"));

			data.setLittleEndian32(0x0a0000); // out_uint32_le(s, 0x0a0000);
			data.setLittleEndian32(0x050000); // out_uint32_le(s, 0x050000);
			data.setLittleEndian32(3); // out_uint32_le(s, 3);
			data.setLittleEndian32(0); // out_uint32_le(s, 0);
			data.setLittleEndian32(0); // out_uint32_le(s, 0);

			data.outUnicodeString("GTB, sommartid", 2 * "GTB, sommartid"
					.length()); // rdp_out_unistr(s, "GTB, sommartid", 2 *
			// strlen("GTB, sommartid"));
			data.incrementPosition(62 - 2 * "GTB, sommartid".length()); // out_uint8s(s,
			// 62 -
			// 2 *
			// strlen("GTB,
			// sommartid"));

			data.setLittleEndian32(0x30000); // out_uint32_le(s, 0x30000);
			data.setLittleEndian32(0x050000); // out_uint32_le(s, 0x050000);
			data.setLittleEndian32(2); // out_uint32_le(s, 2);
			data.setLittleEndian32(0); // out_uint32(s, 0);
			data.setLittleEndian32(0xffffffc4); // out_uint32_le(s, 0xffffffc4);
			data.setLittleEndian32(0xfffffffe); // out_uint32_le(s, 0xfffffffe);
			data.setLittleEndian32(options.rdp5_performanceflags); // out_uint32_le(s,
			// 0x0f);
			data.setLittleEndian32(0); // out_uint32(s, 0);
		}

		data.markEnd();
		byte[] buffer = new byte[data.getEnd()];
		data.copyToByteArray(buffer, 0, 0, data.getEnd());
		SecureLayer.send(data, sec_flags);
	}

	/**
	 * Process an activation demand from the server (received between licence
	 * negotiation and 1st order)
	 *
	 * @param data
	 *            Packet containing demand at current read position
	 * @throws RdesktopException
	 * @throws IOException
	 * @throws OrderException
	 */
	private void processDemandActive(RdpPacket data)
			throws RdesktopException, IOException,
			OrderException {
		int type[] = new int[1];
		int len_combined_caps;

		/* at this point we need to ensure that we have ui created */
		//rd_create_ui();

		this.rdp_shareid = data.getLittleEndian32(); //in_uint32_le(s, g_rdp_shareid);
		data.getLittleEndian16(); // in_uint16_le(s, len_src_descriptor); // ignored
		len_combined_caps = data.getLittleEndian16(); // in_uint16_le(s, len_combined_caps);
		data.get8(); // Overwriting??? // in_uint8s(s, len_src_descriptor); // ignored
		data.incrementPosition(3); // changed - why is this needed?
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("process_demand_active(), shareid=0x" + Integer.toHexString(rdp_shareid));
		}
		processServerCaps(data, len_combined_caps);

		this.sendConfirmActive();

		this.sendSynchronize();
		this.sendControl(RDP_CTL_COOPERATE);
		this.sendControl(RDP_CTL_REQUEST_CONTROL);

		this.receive(type); // Receive RDP_PDU_SYNCHRONIZE
		this.receive(type); // Receive RDP_CTL_COOPERATE
		this.receive(type); // Receive RDP_CTL_GRANT_CONTROL

		this.sendInput(0, InputType.SYNC, 0, 0, 0);
		this.sendFonts(1);
		this.sendFonts(2);

		this.receive(type); // Receive an unknown PDU Code = 0x28

		this.orders.resetOrderState();
	}

	/**
	 * Process a data PDU received from the server
	 *
	 * @param data
	 *            Packet containing data PDU at current read position
	 * @return If non-null, the disconnect error code.
	 * @throws RdesktopException
	 * @throws OrderException
	 */
	private Integer processData(RdpPacket data)
			throws RdesktopException, OrderException {
		int data_type;

		data.incrementPosition(6); // skip shareid, pad, streamid
		data.getLittleEndian16(); // length - ignored?
		data_type = data.get8();
		data.get8(); // compression type (ignored?)
		data.getLittleEndian16(); // compression length (ignored?)
		// clen -= 18; // why do we need to subtract 18 from compression length?

		switch (data_type) {

		case (Rdp.RDP_DATA_PDU_UPDATE):
			LOGGER.debug("Rdp.RDP_DATA_PDU_UPDATE");
			this.processUpdate(data);
		break;

		case RDP_DATA_PDU_CONTROL:
			LOGGER.debug(("Received Control PDU\n"));
			break;

		case RDP_DATA_PDU_SYNCHRONISE:
			LOGGER.debug(("Received Sync PDU\n"));
			break;

		case (Rdp.RDP_DATA_PDU_POINTER):
			LOGGER.debug("Received pointer PDU");
			this.processPointer(data);
		break;
		case (Rdp.RDP_DATA_PDU_BELL):
			LOGGER.debug("Received bell PDU");
			Toolkit tx = Toolkit.getDefaultToolkit();
			tx.beep();
		break;
		case (Rdp.RDP_DATA_PDU_LOGON):
			LOGGER.debug("User logged on");
			if (state != InitState.READY_TO_SEND) {
				state = InitState.LOGGED_ON;
				callback.stateChanged(state);
			}
		break;
		case RDP_DATA_PDU_DISCONNECT:
			/*
			 * Normally received when user logs out or disconnects from a
			 * console session on Windows XP and 2003 Server
			 */
			int code = processDisconnectPdu(data);
			LOGGER.debug(("Received disconnect PDU\n"));
			return code;

		default:
			LOGGER.warn("Unimplemented Data PDU type " + data_type);

		}
		return null;
	}

	private void processUpdate(RdpPacket data) throws OrderException,
	RdesktopException {
		int update_type = 0;

		update_type = data.getLittleEndian16();

		switch (update_type) {

		case (Rdp.RDP_UPDATE_ORDERS):
			data.incrementPosition(2); // pad
		int n_orders = data.getLittleEndian16();
		data.incrementPosition(2); // pad
		this.orders.processOrders(data, next_packet, n_orders);
		break;
		case (Rdp.RDP_UPDATE_BITMAP):
			this.processBitmapUpdates(data);
		break;
		case (Rdp.RDP_UPDATE_PALETTE):
			this.processPalette(data);
		break;
		case (Rdp.RDP_UPDATE_SYNCHRONIZE):
			break;
		default:
			LOGGER.warn("Unimplemented Update type " + update_type);
		}
	}

	private void sendConfirmActive() throws RdesktopException, IOException {
		int caplen = Capset.GENERAL.getLength() + Capset.BITMAP.getLength() + Capset.ORDER.getLength()
				+ Capset.BITMAPCACHE.getLength() + Capset.COLORCACHE.getLength()
				+ Capset.ACTIVATION.getLength() + Capset.CONTROL.getLength() + Capset.POINTER.getLength()
				+ Capset.SHARE.getLength() + Capset.INPUT.getLength() + Capset.SOUND.getLength()
				+ Capset.FONT.getLength() + Capset.GLYPHCACHE.getLength()
				+ 4; // this is a fix for W2k: sessionid

		int sec_flags = (RDP5_FLAG | Secure.SEC_ENCRYPT);

		RdpPacket data = SecureLayer.init(sec_flags, 6 + 14 + caplen
				+ RDP_SOURCE.length);

		// RdpPacket_Localised data = this.init(14 + caplen +
		// RDP_SOURCE.length);

		data.setLittleEndian16(2 + 14 + caplen + RDP_SOURCE.length);
		data.setLittleEndian16((RDP_PDU_CONFIRM_ACTIVE | 0x10));
		data.setLittleEndian16(SecureLayer.McsLayer.getUserID() /* McsUserID() */+ 1001);

		data.setLittleEndian32(this.rdp_shareid);
		data.setLittleEndian16(0x3ea); // user id
		data.setLittleEndian16(RDP_SOURCE.length);
		data.setLittleEndian16(caplen);

		data.copyFromByteArray(RDP_SOURCE, 0, data.getPosition(),
				RDP_SOURCE.length);
		data.incrementPosition(RDP_SOURCE.length);
		data.setLittleEndian16(0xd); // num_caps
		data.incrementPosition(2); // pad

		this.sendGeneralCaps(data);
		// ta.incrementPosition(this.RDP_CAPLEN_GENERAL);
		this.sendBitmapCaps(data);
		this.sendOrderCaps(data);

		if (options.use_rdp5 && options.persistent_bitmap_caching) {
			LOGGER.info("Persistent caching enabled");
			this.sendBitmapcache2Caps(data);
		} else {
			this.sendBitmapcacheCaps(data);
		}

		this.sendColorcacheCaps(data);
		this.sendActivateCaps(data);
		this.sendControlCaps(data);
		this.sendPointerCaps(data);
		this.sendShareCaps(data);

		this.sendInputCaps(data);
		this.sendSoundCaps(data);
		this.sendFontCaps(data);
		this.sendGlyphCacheCaps(data);

		data.markEnd();
		LOGGER.debug("confirm active");
		// this.send(data, RDP_PDU_CONFIRM_ACTIVE);
		this.SecureLayer.send(data, sec_flags);
	}

	private void sendCapHeader(RdpPacket data, Capset capset) {
		data.setLittleEndian16(capset.id);
		data.setLittleEndian16(capset.getLength());
	}

	private void sendGeneralCaps(RdpPacket data) {

		sendCapHeader(data, Capset.GENERAL);

		data.setLittleEndian16(1); /* OS major type */
		data.setLittleEndian16(3); /* OS minor type */
		data.setLittleEndian16(0x200); /* Protocol version */
		data.setLittleEndian16(options.use_rdp5 ? 0x40d : 0);
		// data.setLittleEndian16(options.use_rdp5 ? 0x1d04 : 0); // this seems
		/*
		 * Pad, according to T.128. 0x40d seems to trigger the server to start
		 * sending RDP5 packets. However, the value is 0x1d04 with W2KTSK and
		 * NT4MS. Hmm.. Anyway, thankyou, Microsoft, for sending such
		 * information in a padding field..
		 */
		data.setLittleEndian16(0); /* Compression types */
		data.setLittleEndian16(0); /* Pad */
		data.setLittleEndian16(0); /* Update capability */
		data.setLittleEndian16(0); /* Remote unshare capability */
		data.setLittleEndian16(0); /* Compression level */
		data.setLittleEndian16(0); /* Pad */
	}

	private void sendBitmapCaps(RdpPacket data) {

		sendCapHeader(data, Capset.BITMAP);

		data.setLittleEndian16(options.server_bpp); /* Preferred BPP */
		data.setLittleEndian16(1); /* Receive 1 BPP */
		data.setLittleEndian16(1); /* Receive 4 BPP */
		data.setLittleEndian16(1); /* Receive 8 BPP */
		data.setLittleEndian16(options.width); /* Desktop width */
		data.setLittleEndian16(options.height); /* Desktop height */
		data.setLittleEndian16(0); /* Pad */
		data.setLittleEndian16(1); /* Allow resize */
		data.setLittleEndian16(options.bitmap_compression ? 1 : 0); /* Support compression */
		data.set8(0); /* highColorFlags */
		data.set8(0); /* drawingFlags */
		data.setLittleEndian16(1); /* multipleRectangleSupport */
		data.setLittleEndian16(0); /* Pad */
	}

	private void sendOrderCaps(RdpPacket data) {

		sendCapHeader(data, Capset.ORDER);

		data.incrementPosition(16); /* Terminal desc */
		data.incrementPosition(4); /* Pad */
		data.setLittleEndian16(1); /* Cache X granularity */
		data.setLittleEndian16(20); /* Cache Y granularity */
		data.setLittleEndian16(0); /* Pad */
		data.setLittleEndian16(1); /* Max order level */
		data.setLittleEndian16(0x147); /* Number of fonts */
		int orderFlags = 0x2a;
		orderFlags |= 0x0002; // NEGOTIATEORDERSUPPORT, required
		orderFlags |= 0x0008; // ZEROBOUNDSDELTASSUPPORT, required
		orderFlags |= 0x0020; // COLORINDEXSUPPORT, optional but provided
		// orderFlags |= 0x0040; // SOLIDPATTERNBRUSHONLY, not supported
		// orderFlags |= 0x0080; // ORDERFLAGS_EXTRA_FLAGS, Indicates that a previously pad value contains data
		data.setLittleEndian16(orderFlags); /* Capability flags */
		byte[] order_caps = new byte[32];
		for (PrimaryOrder order : orders.getSupportedPrimaryOrders()) {
			order_caps[order.negociationNumber] = 1;
		}
		data.copyFromByteArray(order_caps, 0, data.getPosition(), 32); /* Orders supported */
		data.incrementPosition(32);
		int textFlags = 0x6a1;
		data.setLittleEndian16(textFlags); /* Text capability flags, ignored (why are we setting this?) */
		int orderFlagsEx = 0;
		// orderFlagsEx |= 0x0002; // ORDERFLAGS_EX_CACHE_BITMAP_REV3_SUPPORT, supports bitmap cache v3
		// orderFlagsEx |= 0x0004; // ORDERFLAGS_EX_ALTSEC_FRAME_MARKER_SUPPORT, supports secondary drawing
		data.setLittleEndian16(orderFlagsEx); /* More order flags */
		data.incrementPosition(4); /* Pad */
		data.setLittleEndian32(SAVE_DESKTOP ? 0x38400 : 0); /* Desktop cache size */
		data.incrementPosition(4); /* Pad */
		int textANSICodePage = 0x4e4;
		data.setLittleEndian16(textANSICodePage); /* textANSICodePage */
		data.setLittleEndian16(0); /* Pad */
	}

	private void sendBitmapcacheCaps(RdpPacket data) {

		sendCapHeader(data, Capset.BITMAPCACHE);

		data.incrementPosition(24); /* unused */
		data.setLittleEndian16(0x258); /* entries */
		data.setLittleEndian16(0x100); /* max cell size */
		data.setLittleEndian16(0x12c); /* entries */
		data.setLittleEndian16(0x400); /* max cell size */
		data.setLittleEndian16(0x106); /* entries */
		data.setLittleEndian16(0x1000); /* max cell size */
	}

	/* Output bitmap cache v2 capability set */
	private void sendBitmapcache2Caps(RdpPacket data) {
		sendCapHeader(data, Capset.BITMAPCACHE_REV2);

		data.setLittleEndian16(options.persistent_bitmap_caching ? 2 : 0); /* version */

		data.setBigEndian16(3); /* number of caches in this set */

		/* max cell size for cache 0 is 16x16, 1 = 32x32, 2 = 64x64, etc */
		data.setLittleEndian32(BMPCACHE2_C0_CELLS); // out_uint32_le(s,
		// BMPCACHE2_C0_CELLS);
		data.setLittleEndian32(BMPCACHE2_C1_CELLS); // out_uint32_le(s,
		// BMPCACHE2_C1_CELLS);

		// data.setLittleEndian32(PstCache.pstcache_init(2) ?
		// (BMPCACHE2_NUM_PSTCELLS | BMPCACHE2_FLAG_PERSIST) :
		// BMPCACHE2_C2_CELLS);

		if (cache.pstCache.pstcache_init(2)) { // XXX this WILL cause corruption, possibly
			LOGGER.info("Persistent cache initialized");
			data.setLittleEndian32(BMPCACHE2_NUM_PSTCELLS
					| BMPCACHE2_FLAG_PERSIST);
		} else {
			LOGGER.info("Persistent cache not initialized");
			data.setLittleEndian32(BMPCACHE2_C2_CELLS);
		}
		data.incrementPosition(20); // out_uint8s(s, 20); /* other bitmap caches
		// not used */
	}

	private void sendColorcacheCaps(RdpPacket data) {

		sendCapHeader(data, Capset.COLORCACHE);

		data.setLittleEndian16(6); /* cache size */
		data.setLittleEndian16(0); /* pad */
	}

	private void sendActivateCaps(RdpPacket data) {

		sendCapHeader(data, Capset.ACTIVATION);

		data.setLittleEndian16(0); /* Help key */
		data.setLittleEndian16(0); /* Help index key */
		data.setLittleEndian16(0); /* Extended help key */
		data.setLittleEndian16(0); /* Window activate */
	}

	private void sendControlCaps(RdpPacket data) {

		sendCapHeader(data, Capset.CONTROL);

		data.setLittleEndian16(0); /* Control capabilities */
		data.setLittleEndian16(0); /* Remote detach */
		data.setLittleEndian16(2); /* Control interest */
		data.setLittleEndian16(2); /* Detach interest */
	}

	private void sendPointerCaps(RdpPacket data) {

		sendCapHeader(data, Capset.POINTER);

		data.setLittleEndian16(0); /* Colour pointer */
		data.setLittleEndian16(20); /* Cache size */
	}

	private void sendShareCaps(RdpPacket data) {

		sendCapHeader(data, Capset.SHARE);

		data.setLittleEndian16(0); /* userid */
		data.setLittleEndian16(0); /* pad */
	}

	private void sendSoundCaps(RdpPacket data) {
		sendCapHeader(data, Capset.SOUND);

		int flags = 0;
		flags |= 0x0001; // Supports beeps
		data.setLittleEndian16(flags); /* flags */
		data.setLittleEndian16(0); /* pad */
	}

	private void sendInputCaps(RdpPacket data) {
		sendCapHeader(data, Capset.INPUT);

		int flags = 0;
		flags |= 0x0001; // Supports beeps
		data.setLittleEndian16(flags); /* flags */
		data.setLittleEndian16(0); /* pad */

		data.setLittleEndian32(options.keylayout); // Keyboard layout
		data.setLittleEndian32(0x04); // Keyboard type, 4 = IBM enhanced (is this correct?)
		data.setLittleEndian32(0); // Subtype
		data.setLittleEndian32(12); // Number of function keys.  Hm, what would a keyboard with Integer.MAX_VALUE keys look like?

		data.incrementPosition(64); // 64 null bytes, for the file name
	}

	private void sendFontCaps(RdpPacket data) {
		sendCapHeader(data, Capset.FONT);

		int flags = 0;
		flags |= 0x0001; // Font list
		data.setLittleEndian16(flags); // Flags
		data.setLittleEndian16(0);  // Pad
	}

	private void sendGlyphCacheCaps(RdpPacket data) {
		sendCapHeader(data, Capset.GLYPHCACHE);

		// These are the definitions that were previously sent - no clue if
		// they're meaningful
		writeCacheDefinition(data, 0xFE, 0x04);
		writeCacheDefinition(data, 0xFE, 0x04);
		writeCacheDefinition(data, 0xFE, 0x08);
		writeCacheDefinition(data, 0xFE, 0x08);
		writeCacheDefinition(data, 0xFE, 0x10);
		writeCacheDefinition(data, 0xFE, 0x20);
		writeCacheDefinition(data, 0xFE, 0x40);
		writeCacheDefinition(data, 0xFE, 0x80);
		writeCacheDefinition(data, 0xFE, 0x0100);
		writeCacheDefinition(data, 0x40, 0x0800);

		data.setLittleEndian32(0x10001000); // FragCache - this seems like a bad value
		data.setLittleEndian16(2); // GLYPH_SUPPORT_FULL
		data.setLittleEndian16(0); // Pad
	}

	/** Writes a TS_CACHE_DEFINITION */
	private void writeCacheDefinition(RdpPacket data, int entries, int cellSize) {
		assert entries <= 254;
		assert cellSize <= 2048;
		data.setLittleEndian16(entries);
		data.setLittleEndian16(cellSize);
	}

	private void sendSynchronize() throws RdesktopException, IOException {
		RdpPacket data = this.initData(4);

		data.setLittleEndian16(1); // type
		data.setLittleEndian16(1002);

		data.markEnd();
		LOGGER.debug("sync");
		this.sendData(data, RDP_DATA_PDU_SYNCHRONISE);
	}

	private void sendControl(int action) throws RdesktopException, IOException {

		RdpPacket data = this.initData(8);

		data.setLittleEndian16(action);
		data.setLittleEndian16(0); // userid
		data.setLittleEndian32(0); // control id

		data.markEnd();
		LOGGER.debug("control");
		this.sendData(data, RDP_DATA_PDU_CONTROL);
	}

	/**
	 * Sends an Input PDU
	 * @param time A timestamp, ignored by the server.
	 * @param message_type The type of input PDU.
	 * @param device_flags The first 16-bit parameter, which is usually flags.
	 * @param param1 The second 16-bit parameter
	 * @param param2 The third 16-bit parameter
	 * @see [MS-RDPBCGR] 2.2.8.1.1.3.1
	 */
	public void sendInput(int time, InputType message_type, int device_flags,
			int param1, int param2) {
		RdpPacket data = null;
		try {
			data = this.initData(16);
		} catch (RdesktopException e) {
			LOGGER.warn("Error preping input packet", e);
			this.callback.error(e, this);
		}

		data.setLittleEndian16(1); /* number of events */
		data.setLittleEndian16(0); /* pad */

		data.setLittleEndian32(time);
		data.setLittleEndian16(message_type.id);
		data.setLittleEndian16(device_flags);
		data.setLittleEndian16(param1);
		data.setLittleEndian16(param2);

		data.markEnd();
		// logger.info("input");
		// if(logger.isInfoEnabled()) logger.info(data);

		try {
			this.sendData(data, RDP_DATA_PDU_INPUT);
		} catch (RdesktopException r) {
			LOGGER.warn("Error sending input packet", r);
			this.callback.error(r, this);
		} catch (IOException i) {
			LOGGER.warn("Unexpected IOException", i);
			this.callback.error(i, this);
		}
	}

	private void sendFonts(int seq) throws RdesktopException, IOException {

		RdpPacket data = this.initData(8);

		data.setLittleEndian16(0); /* number of fonts */
		data.setLittleEndian16(0x3e); /* unknown */
		data.setLittleEndian16(seq); /* unknown */
		data.setLittleEndian16(0x32); /* entry size */

		data.markEnd();
		LOGGER.debug("fonts");
		this.sendData(data, RDP_DATA_PDU_FONT2);
	}

	private void processPointer(RdpPacket data)
			throws RdesktopException {
		int message_type = 0;
		int x = 0, y = 0;

		message_type = data.getLittleEndian16();
		data.incrementPosition(2);
		switch (message_type) {

		case (Rdp.RDP_POINTER_MOVE):
			LOGGER.debug("Rdp.RDP_POINTER_MOVE");
		x = data.getLittleEndian16();
		y = data.getLittleEndian16();

		if (data.getPosition() <= data.getEnd()) {
			callback.movePointer(x, y);
		}
		break;

		case (Rdp.RDP_POINTER_COLOR):
			process_colour_pointer_pdu(data);
		break;

		case (Rdp.RDP_POINTER_CACHED):
			process_cached_pointer_pdu(data);
		break;

		case RDP_POINTER_SYSTEM:
			process_system_pointer_pdu(data);
			break;

		default:
			break;
		}
	}

	private void process_system_pointer_pdu(RdpPacket data) {
		int system_pointer_type = 0;

		data.getLittleEndian16(system_pointer_type); // in_uint16(s,
		// system_pointer_type);
		switch (system_pointer_type) {
		case RDP_NULL_POINTER:
			LOGGER.debug("RDP_NULL_POINTER");
			callback.setCursor(null);
			break;

		default:
			LOGGER.warn("Unimplemented system pointer message 0x"
					+ Integer.toHexString(system_pointer_type));
			// unimpl("System pointer message 0x%x\n", system_pointer_type);
		}
	}

	protected void processBitmapUpdates(RdpPacket data)
			throws RdesktopException {
		LOGGER.debug("processBitmapUpdates");
		int n_updates = 0;
		int left = 0, top = 0, right = 0, bottom = 0, width = 0, height = 0;
		int cx = 0, cy = 0, bitsperpixel = 0, compression = 0, buffersize = 0, size = 0;
		byte[] pixel = null;

		int minX, minY, maxX, maxY;

		maxX = maxY = 0;
		minX = options.width;
		minY = options.height;

		n_updates = data.getLittleEndian16();

		for (int i = 0; i < n_updates; i++) {

			left = data.getLittleEndian16();
			top = data.getLittleEndian16();
			right = data.getLittleEndian16();
			bottom = data.getLittleEndian16();
			width = data.getLittleEndian16();
			height = data.getLittleEndian16();
			bitsperpixel = data.getLittleEndian16();
			int Bpp = (bitsperpixel + 7) / 8;
			compression = data.getLittleEndian16();
			buffersize = data.getLittleEndian16();

			cx = right - left + 1;
			cy = bottom - top + 1;

			if (minX > left) {
				minX = left;
			}
			if (minY > top) {
				minY = top;
			}
			if (maxX < right) {
				maxX = right;
			}
			if (maxY < bottom) {
				maxY = bottom;
			}

			/* Server may limit bpp - this is how we find out */
			if (options.server_bpp != bitsperpixel) {
				LOGGER.warn("Server limited colour depth to " + bitsperpixel
						+ " bits");
				options.set_bpp(bitsperpixel);
			}

			if (compression == 0) {
				// logger.info("compression == 0");
				pixel = new byte[width * height * Bpp];

				for (int y = 0; y < height; y++) {
					data.copyToByteArray(pixel, (height - y - 1)
							* (width * Bpp), data.getPosition(), width * Bpp);
					data.incrementPosition(width * Bpp);
				}

				surface.displayImage(Bitmap.convertImage(options, pixel, Bpp), width,
						height, left, top, cx, cy);
				continue;
			}

			if ((compression & 0x400) != 0) {
				// logger.info("compression & 0x400 != 0");
				size = buffersize;
			} else {
				// logger.info("compression & 0x400 == 0");
				data.incrementPosition(2); // pad
				size = data.getLittleEndian16();

				data.incrementPosition(4); // line size, final size

			}
			if (Bpp == 1) {
				pixel = Bitmap.decompress(options, width, height, size, data, Bpp);
				if (pixel != null) {
					surface.displayImage(Bitmap.convertImage(options, pixel, Bpp),
							width, height, left, top, cx, cy);
				} else {
					LOGGER.warn("Could not decompress bitmap");
				}
			} else {

				if (options.bitmap_decompression_store == Options.INTEGER_BITMAP_DECOMPRESSION) {
					int[] pixeli = Bitmap.decompressInt(options, width, height, size,
							data, Bpp);
					if (pixeli != null) {
						surface.displayImage(pixeli, width, height, left, top,
								cx, cy);
					} else {
						LOGGER.warn("Could not decompress bitmap");
					}
				} else if (options.bitmap_decompression_store == Options.BUFFEREDIMAGE_BITMAP_DECOMPRESSION) {
					Image pix = Bitmap.decompressImg(options, width, height, size, data,
							Bpp, null);
					if (pix != null) {
						surface.displayImage(pix, left, top);
					} else {
						LOGGER.warn("Could not decompress bitmap");
					}
				} else {
					surface.displayCompressed(left, top, width, height, size,
							data, Bpp, null);
				}
			}
		}
	}

	protected void processPalette(RdpPacket data) {
		int n_colors = 0;
		IndexColorModel cm = null;
		byte[] palette = null;

		byte[] red = null;
		byte[] green = null;
		byte[] blue = null;
		int j = 0;

		data.incrementPosition(2); // pad
		n_colors = data.getLittleEndian16(); // Number of Colors in Palette
		data.incrementPosition(2); // pad
		palette = new byte[n_colors * 3];
		red = new byte[n_colors];
		green = new byte[n_colors];
		blue = new byte[n_colors];
		data.copyToByteArray(palette, 0, data.getPosition(), palette.length);
		data.incrementPosition(palette.length);
		for (int i = 0; i < n_colors; i++) {
			red[i] = palette[j];
			green[i] = palette[j + 1];
			blue[i] = palette[j + 2];
			j += 3;
		}
		cm = new IndexColorModel(8, n_colors, red, green, blue);
		surface.registerPalette(cm);
	}

	public void registerDrawingSurface(RdesktopCallback callback) {
		this.callback = callback;
		this.surface.registerCallback(callback);
		callback.registerSurface(this.surface);
		orders.registerDrawingSurface(this.surface);
	}

	/* Process a null system pointer PDU */
	protected void process_null_system_pointer_pdu(RdpPacket s)
			throws RdesktopException {
		// FIXME: We should probably set another cursor here,
		// like the X window system base cursor or something.
		callback.setCursor(cache.getCursor(0));
	}

	protected void process_colour_pointer_pdu(RdpPacket data)
			throws RdesktopException {
		LOGGER.debug("Rdp.RDP_POINTER_COLOR");
		int x = 0, y = 0, width = 0, height = 0, cache_idx = 0, masklen = 0, datalen = 0;
		byte[] mask = null, pixel = null;
		Cursor cursor = null;

		cache_idx = data.getLittleEndian16();
		x = data.getLittleEndian16();
		y = data.getLittleEndian16();
		width = data.getLittleEndian16();
		height = data.getLittleEndian16();
		masklen = data.getLittleEndian16();
		datalen = data.getLittleEndian16();
		mask = new byte[masklen];
		pixel = new byte[datalen];
		data.copyToByteArray(pixel, 0, data.getPosition(), datalen);
		data.incrementPosition(datalen);
		data.copyToByteArray(mask, 0, data.getPosition(), masklen);
		data.incrementPosition(masklen);
		cursor = callback.createCursor(x, y, width, height, mask, pixel,
				cache_idx);
		// logger.info("Creating and setting cursor " + cache_idx);
		callback.setCursor(cursor);
		cache.putCursor(cache_idx, cursor);
	}

	protected void process_cached_pointer_pdu(RdpPacket data)
			throws RdesktopException {
		LOGGER.debug("Rdp.RDP_POINTER_CACHED");
		int cache_idx = data.getLittleEndian16();
		// logger.info("Setting cursor "+cache_idx);
		callback.setCursor(cache.getCursor(cache_idx));
	}
}
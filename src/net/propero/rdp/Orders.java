/* Orders.java
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

import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.propero.rdp.orders.BoundsOrder;
import net.propero.rdp.orders.Brush;
import net.propero.rdp.orders.DeskSaveOrder;
import net.propero.rdp.orders.DestBltOrder;
import net.propero.rdp.orders.LineOrder;
import net.propero.rdp.orders.MemBltOrder;
import net.propero.rdp.orders.PatBltOrder;
import net.propero.rdp.orders.Pen;
import net.propero.rdp.orders.PolyLineOrder;
import net.propero.rdp.orders.RectangleOrder;
import net.propero.rdp.orders.ScreenBltOrder;
import net.propero.rdp.orders.Text2Order;
import net.propero.rdp.orders.TriBltOrder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parses and tracks RDP orders, and then delegates the actual drawing.
 */
public class Orders {
	private static final Logger LOGGER = LogManager.getLogger();

	private OrderState os = null;

	private OrderSurface surface = null;

	public Cache cache = null;

	/* RDP_BMPCACHE2_ORDER */
	private static final int ID_MASK = 0x0007;

	private static final int MODE_MASK = 0x0038;

	private static final int SQUARE = 0x0080;

	private static final int PERSIST = 0x0100;

	private static final int FLAG_51_UNKNOWN = 0x0800;

	private static final int MODE_SHIFT = 3;

	private static final int LONG_FORMAT = 0x80;

	private static final int BUFSIZE_MASK = 0x3FFF; /* or 0x1FFF? */

	/**
	 * Flags to indicate the type of an order.  More flags may exist.
	 *
	 * If the TS_STANDARD (0x01) flag is set, the order is a primary drawing
	 * order. If both the TS_STANDARD (0x01) and TS_SECONDARY (0x02) flags are
	 * set, the order is a secondary drawing order. Finally, if only the
	 * TS_SECONDARY (0x02) flag is set, the order is an alternate secondary
	 * drawing order.
	 */
	private static final int RDP_ORDER_STANDARD = 0x01, RDP_ORDER_SECONDARY = 0x02;

	/**
	 * Flags that are used with primary drawing orders. Not all flags are from
	 * the same type.
	 *
	 * @see [MS-RDPEGDI] 2.2.2.2.1.1.2 controlFlags, bounds
	 */
	private static class PrimaryOrderFlags {
		/**
		 * Indicates that the order has a bounding rectangle.
		 */
		private static final int BOUNDS = 0x04;

		/**
		 * Indicates that the order type has changed and that the orderType field is
		 * present.
		 */
		private static final int TYPE_CHANGE = 0x08;

		/**
		 * Indicates that all of the Coord-type fields in the order (see section
		 * 2.2.2.2.1.1.1.1) are specified as 1-byte signed deltas from their
		 * previous values.
		 */
		private static final int DELTA_COORDINATES = 0x10;

		/**
		 * Indicates that the previous bounding rectangle MUST be used, as the
		 * bounds have not changed (this implies that the bounds field is not
		 * present). This flag is only applicable if the TS_BOUNDS (0x04) flag is
		 * set.
		 */
		private static final int ZERO_BOUNDS_DELTAS = 0x20;

		/**
		 * Used to form a 2-bit count (so maximum of 3) of the number of field
		 * flag bytes (present in the fieldFlags field) that are zero and not
		 * present, counted from the end of the set of field flag bytes.
		 */
		private static final int ZERO_FIELD_BYTE_BIT0 = 0x40, ZERO_FIELD_BYTE_BIT1 = 0x80;

		// --- bounds flags ---
		/** Indicates that the left bound is present and encoded as a 2-byte, little-endian ordered value. */
		private static final int BOUND_LEFT = 0x01;
		/** Indicates that the top bound is present and encoded as a 2-byte, little-endian ordered value. */
		private static final int BOUND_TOP = 0x02;
		/** Indicates that the right bound is present and encoded as a 2-byte, little-endian ordered value. */
		private static final int BOUND_RIGHT = 0x04;
		/** Indicates that the bottom bound is present and encoded as a 2-byte, little-endian ordered value. */
		private static final int BOUND_BOTTOM = 0x08;
		/** Indicates that the left bound is present and encoded as a 1-byte signed value used as an offset (-128 to 127) from the previous value. */
		private static final int BOUND_DELTA_LEFT = 0x10;
		/** Indicates that the top bound is present and encoded as a 1-byte signed value used as an offset (-128 to 127) from the previous value. */
		private static final int BOUND_DELTA_TOP = 0x20;
		/** Indicates that the right bound is present and encoded as a 1-byte, signed value used as an offset (-128 to 127) from the previous value. */
		private static final int BOUND_DELTA_RIGHT = 0x40;
		/** Indicates that the bottom bound is present and encoded as a 1-byte, signed value used as an offset (-128 to 127) from the previous value. */
		private static final int BOUND_DELTA_BOTTOM = 0x80;
	}

	private static final int MIX_TRANSPARENT = 0;

	private static final int MIX_OPAQUE = 1;

	private static final int TEXT2_VERTICAL = 0x04;

	private static final int TEXT2_IMPLICIT_X = 0x20;

	/**
	 * Different primary drawing orders. All section indications in docs inside are for
	 * [MS-RDPEGDI] unless otherwise noted.
	 *
	 * @see [MS-RDPBCGR] section 2.2.7.1.3
	 * @see [MS-RDPEGDI] section 2.2.2.2.1.1.2
	 */
	public static enum PrimaryOrder {
		/** DstBlt (section 2.2.2.2.1.1.2.1) Primary Drawing Order. */
		DSTBLT(0x00, 0x00, 5, 1, 9),
		/** MultiDstBlt (section 2.2.2.2.1.1.2.2) Primary Drawing Order. */
		MULTI_DSTBLT(0x0F, 0x0F, 7, 1, 395),
		/** PatBlt (section 2.2.2.2.1.1.2.3) Primary Drawing Order. */
		PATBLT(0x01, 0x01, 12, 2, 26),
		/** MultiPatBlt (section 2.2.2.2.1.1.2.4) Primary Drawing Order. */
		MULTI_PATBLT(0x10, 0x10, 14, 2, 412),
		/** OpaqueRect (section 2.2.2.2.1.1.2.5) Primary Drawing Order. */
		OPAQUERECT(0x0A, 0x01, 7, 1, 11), // NOTE: negotiation id 0x01 is registered to both OpaqueRect and PatBlt
		/** MultiOpaqueRect (section 2.2.2.2.1.1.2.6) Primary Drawing Order. */
		MULTI_OPAQUERECT(0x12, 0x12, 9, 2, 397),
		/** ScrBlt (section 2.2.2.2.1.1.2.7) Primary Drawing Order. */
		SCRBLT(0x02, 0x02, 7, 1, 13),
		/** MultiScrBlt (section 2.2.2.2.1.1.2.8) Primary Drawing Order. */
		MULTI_SCRBLT(0x11, 0x11, 9, 2, 399),
		/** MemBlt (section 2.2.2.2.1.1.2.9) Primary Drawing Order. */
		MEMBLT(0x0D, 0x03, 9, 2, 17),
		/** Mem3Blt (section 2.2.2.2.1.1.2.10) Primary Drawing Order. */
		MEM3BLT(0x0E, 0x04, 16, 3, 34),
		/** LineTo (section 2.2.2.2.1.1.2.11) Primary Drawing Order. */
		LINETO(0x09, 0x08, 10, 2, 19),
		/** SaveBitmap (section 2.2.2.2.1.1.2.12) Primary Drawing Order. */
		SAVEBITMAP(0x0B, 0x0B, 6, 1, 13),
		/** GlyphIndex (section 2.2.2.2.1.1.2.13) Primary Drawing Order. */
		GLYPHINDEX(0x1B, 0x1B, 22, 3, 297),
		/** FastIndex (section 2.2.2.2.1.1.2.14) Primary Drawing Order. */
		FASTINDEX(0x13, 0x13, 15, 2, 285),
		/** FastGlyph (section 2.2.2.2.1.1.2.15) Primary Drawing Order. */
		FASTGLPHY(0x18, 0x18, 15, 2, 285),
		/** PolygonSC (section 2.2.2.2.1.1.2.16) Primary Drawing Order. */
		POLYGON_SC(0x14, 0x14, 7, 1, 249),
		/** PolygonCB (section 2.2.2.2.1.1.2.17) Primary Drawing Order. */
		POLYGON_CB(0x15, 0x15, 13, 2, 263),
		/** Polyline (section 2.2.2.2.1.1.2.18) Primary Drawing Order. */
		POLYLINE(0x16, 0x16, 7, 1, 148),
		/** EllipseSC (section 2.2.2.2.1.1.2.19) Primary Drawing Order. */
		ELLIPSE_SC(0x19, 0x19, 7, 1, 13),
		/** EllipseCB (section 2.2.2.2.1.1.2.20) Primary Drawing Order. */
		ELLIPSE_CB(0x1A, 0x1A, 13, 2, 27),
		/** DrawNineGrid (section 2.2.2.2.1.1.2.21) Primary Drawing Order. */
		DRAWNINEGRID(0x07, 0x07, 5, 1, 10),
		/** MultiDrawNineGrid (section 2.2.2.2.1.1.2.22) Primary Drawing Order. */
		MULTIDRAWLINEGRID(0x08, 0x09, 7, 1, 396);

		public final int encodingNumber;
		public final int negociationNumber;
		public final int numFields;
		public final int numFieldBytes;
		public final int maxLength;

		/**
		 * Properties based off of the block of text next to each field.
		 *
		 * @param encodingNumber
		 *            Number used for orderType
		 * @param negociationNumber
		 *            Index used for orderSupport
		 * @param numFields
		 *            Number of fields in the fieldFlags variable-length field
		 * @param numFieldEncBytes
		 *            Number of bytes for the fieldFlags variable; must be equal
		 *            to <code>ceil(((numberOfOrderFields) + 1) / 8)</code>. I'm
		 *            only including this for testing; maybe it isn't needed.
		 * @param maxLength
		 *            Maximum size of the order in bytes
		 */
		private PrimaryOrder(int encodingNumber, int negociationNumber, int numFields,
				int numFieldEncBytes, int maxLength) {
			if (encodingNumber < 0 || encodingNumber > 31) {
				// "Soft" because the 31 requirement isn't imposed elsewhere, but we use it for array sizes.
				// If something needs a larger number, this one can be boosted (others are protocol-required)
				throw new AssertionError("Invalid encoding number " + numFields + "; must be >= 0 and <= 31 (soft)");
			}
			if (negociationNumber < 0 || negociationNumber > 31) {
				throw new AssertionError("Invalid negociation number " + numFields + "; must be >= 0 and <= 31");
			}
			if (numFields < 0 || numFields > 23) {
				throw new AssertionError("Invalid field count " + numFields + "; must be >= 0 and <= 23");
			}
			int expectedFieldCount = (int) Math.ceil((numFields + 1) / 8.0);
			if (numFieldEncBytes != expectedFieldCount) {
				throw new AssertionError("Expected the number of field bytes to be " + expectedFieldCount + " but was specified as " + numFieldEncBytes);
			}

			this.encodingNumber = encodingNumber;
			this.negociationNumber = negociationNumber;
			this.numFields = numFields;
			this.numFieldBytes = numFieldEncBytes;
			this.maxLength = maxLength;
		}

		@Override
		public String toString() {
			return super.toString() + " (encoding=0x" + Integer.toHexString(this.encodingNumber) + ", negociation=0x" + Integer.toHexString(this.negociationNumber) + ", numFields=" + numFields + ", numFieldBytes=" + numFieldBytes + ", maxLength=" + maxLength + ")";
		}

		private static final PrimaryOrder[] BY_ENCODING_NUMBER;
		private static final PrimaryOrder[] BY_NEGOCIATION_NUMBER;

		static {
			BY_ENCODING_NUMBER = new PrimaryOrder[32]; // This 32 is soft
			BY_NEGOCIATION_NUMBER = new PrimaryOrder[32]; // This is a hard max

			for (PrimaryOrder order : values()) {
				if (BY_ENCODING_NUMBER[order.encodingNumber] != null) {
					throw new AssertionError("Duplicate encoding number!  "
							+ order.encodingNumber
							+ " is already registered to "
							+ BY_ENCODING_NUMBER[order.encodingNumber]
									+ "; can't register " + order + " over it!");
				}
				// Don't check BY_NEGOCIATION_NUMBER, as (sadly) it's registered
				// twice for some things
				BY_ENCODING_NUMBER[order.encodingNumber] = order;
				BY_NEGOCIATION_NUMBER[order.negociationNumber] = order;
			}
		}

		/**
		 * Gets a PrimaryOrder with the given encoding number.
		 * @param encodingNumber The number
		 * @return The given PrimaryOrder (non-null)
		 * @throws IndexOutOfBoundsException When encodingNumber is too small
		 * @throws IllegalArgumentException If there is no PrimaryOrder with that ID
		 */
		public static PrimaryOrder forEncodingNumber(int encodingNumber) {
			if (encodingNumber < 0) {
				throw new IndexOutOfBoundsException("Invalid encoding number (<0): " + encodingNumber);
			}
			if (encodingNumber >= BY_ENCODING_NUMBER.length || BY_ENCODING_NUMBER[encodingNumber] == null) {
				throw new IllegalArgumentException("No PrimaryOrder with encoding number " + encodingNumber);
			}
			return BY_ENCODING_NUMBER[encodingNumber];
		}

		/**
		 * Gets a PrimaryOrder with the given negotiation number.
		 * @param negociationNumber The number
		 * @return The given PrimaryOrder (non-null)
		 * @throws IndexOutOfBoundsException When negociationNumber is too small or too big
		 * @throws IllegalArgumentException If there is no PrimaryOrder with that ID
		 */
		public static PrimaryOrder forNegociationNumber(int negociationNumber) {
			if (negociationNumber < 0 || negociationNumber > 31) {
				throw new IndexOutOfBoundsException("Invalid encoding number (<0 or >31): " + negociationNumber);
			}
			if (BY_NEGOCIATION_NUMBER[negociationNumber] == null) {
				throw new IllegalArgumentException("No PrimaryOrder with negociation number " + negociationNumber);
			}
			return BY_NEGOCIATION_NUMBER[negociationNumber];
		}
	}

	/**
	 * Different secondary (caching) orders.
	 *
	 * @see [MS-RDPEGDI] 2.2.2.2.1.2.1.1 (orderType)
	 */
	public static enum SecondaryOrder {
		/** Cache Bitmap - Revision 1 (section 2.2.2.2.1.2.2) Secondary Drawing Order with an uncompressed bitmap. */
		BITMAP_UNCOMPRESSED(0x00),
		/** Cache Color Table (section 2.2.2.2.1.2.4) Secondary Drawing Order. */
		COLOR_TABLE(0x01),
		/** Cache Bitmap - Revision 1 (section 2.2.2.2.1.2.2) Secondary Drawing Order with a compressed bitmap. */
		BITMAP_COMPRESSED(0x02),
		/** Cache Glyph - Revision 1 (section 2.2.2.2.1.2.5) or Cache Glyph - Revision 2 (section 2.2.2.2.1.2.6) Secondary Drawing Order. The version is indicated by the extraFlags field. */
		GLYPH(0x03),
		/** Cache Bitmap - Revision 2 (section 2.2.2.2.1.2.3) Secondary Drawing Order with an uncompressed bitmap. */
		BITMAP_UNCOMPRESSED_REV2(0x04),
		/** Cache Bitmap - Revision 2 (section 2.2.2.2.1.2.3) Secondary Drawing Order with a compressed bitmap. */
		BITMAP_COMPRESSED_REV2(0x05),
		/** Cache Brush (section 2.2.2.2.1.2.7) Secondary Drawing Order. */
		BRUSH(0x07),
		/** Cache Bitmap - Revision 3 (section 2.2.2.2.1.2.8) Secondary Drawing Order with a compressed bitmap. */
		BITMAP_COMPRESSED_REV3(0x08);

		public final int id;

		/**
		 * @param id The numeric ID for this secondary order.
		 */
		private SecondaryOrder(int id) {
			this.id = id;
		}

		/**
		 * Gets a SecondaryOrder with the given ID.
		 * @param id The ID
		 * @return The given SecondaryOrder (non-null)
		 * @throws IllegalArgumentException If there is no SecondaryOrder with that ID
		 */
		public static SecondaryOrder forId(int id) {
			for (SecondaryOrder order : values()) {
				if (order.id == id) {
					return order;
				}
			}
			throw new IllegalArgumentException("No secondary order with ID " + id);
		}

		@Override
		public String toString() {
			return super.toString() + " (id=0x" + Integer.toHexString(id) + ")";
		}
	}

	/**
	 * Different alternate secondary orders.
	 *
	 * @see [MS-RDPEGDI] 2.2.2.2.1.3.1.1 (orderType)
	 */
	public static enum AltSecondaryOrder {
		/** Switch Surface Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.3). */
		TS_ALTSEC_SWITCH_SURFACE(0x00),
		/** Create Offscreen Bitmap Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.2). */
		TS_ALTSEC_CREATE_OFFSCR_BITMAP(0x01),
		/** Stream Bitmap First (Revision 1 and 2) Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.5.1). */
		TS_ALTSEC_STREAM_BITMAP_FIRST(0x02),
		/** Stream Bitmap Next Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.5.2). */
		TS_ALTSEC_STREAM_BITMAP_NEXT(0x03),
		/** Create NineGrid Bitmap Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.4). */
		TS_ALTSEC_CREATE_NINEGRID_BITMAP(0x04),
		/** Draw GDI+ First Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.6.2). */
		TS_ALTSEC_GDIP_FIRST(0x05),
		/** Draw GDI+ Next Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.6.3). */
		TS_ALTSEC_GDIP_NEXT(0x06),
		/** Draw GDI+ End Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.6.4). */
		TS_ALTSEC_GDIP_END(0x07),
		/** Draw GDI+ First Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.6.2). */
		TS_ALTSEC_GDIP_CACHE_FIRST(0x08),
		/** Draw GDI+ Cache Next Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.6.3). */
		TS_ALTSEC_GDIP_CACHE_NEXT(0x09),
		/** Draw GDI+ Cache End Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.6.4). */
		TS_ALTSEC_GDIP_CACHE_END(0x0A),
		/** Windowing Alternate Secondary Drawing Order (see [MS-RDPERP] section 2.2.1.3). */
		TS_ALTSEC_WINDOW(0x0B),
		/** Desktop Composition Alternate Secondary Drawing Order (see [MS-RDPEDC] section 2.2.1.1). */
		TS_ALTSEC_COMPDESK_FIRST(0x0C),
		/** Frame Marker Alternate Secondary Drawing Order (see section 2.2.2.2.1.3.7) */
		TS_ALTSEC_FRAME_MARKER(0x0D);

		public final int id;

		private AltSecondaryOrder(int id) {
			this.id = id;
		}

		@Override
		public String toString() {
			return super.toString() + " (id=0x" + Integer.toHexString(id) + ")";
		}

		/**
		 * Gets a AltSecondaryOrder with the given ID.
		 * @param id The ID
		 * @return The given AltSecondaryOrder (non-null)
		 * @throws IllegalArgumentException If there is no AltSecondaryOrder with that ID
		 */
		public static AltSecondaryOrder forId(int id) {
			for (AltSecondaryOrder order : values()) {
				if (order.id == id) {
					return order;
				}
			}
			throw new IllegalArgumentException("No secondary order with ID " + id);
		}

		/**
		 * Gets an AltSecondaryOrder for the given ID in the controlFlags field.
		 * The ID for an altsec order is in bits 2 to 7.
		 *
		 * @param controlFlags
		 *            The controlFlags field of a packet
		 * @return The given AltSecondaryOrder (non-null)
		 * @throws IllegalArgumentException If there is no AltSecondaryOrder with that ID
		 */
		public static AltSecondaryOrder forPacketId(int controlFlags) {
			return forId(controlFlags >> 2);
		}
	}

	/**
	 * All primary orders that are supported
	 */
	private final EnumSet<PrimaryOrder> supportedPrimaryOrders;

	protected final Options options;
	public Orders(Options options) {
		os = new OrderState();
		this.options = options;

		supportedPrimaryOrders = EnumSet.noneOf(PrimaryOrder.class);
		supportedPrimaryOrders.add(PrimaryOrder.DSTBLT);
		supportedPrimaryOrders.add(PrimaryOrder.PATBLT);
		supportedPrimaryOrders.add(PrimaryOrder.OPAQUERECT); // Note: same ID as PatBlt
		if (options.bitmap_caching) {
			supportedPrimaryOrders.add(PrimaryOrder.MEMBLT);
		}
		supportedPrimaryOrders.add(PrimaryOrder.SCRBLT);
		supportedPrimaryOrders.add(PrimaryOrder.LINETO);
		if (Rdp.SAVE_DESKTOP) {
			supportedPrimaryOrders.add(PrimaryOrder.SAVEBITMAP);
		}
		supportedPrimaryOrders.add(PrimaryOrder.MEM3BLT);
		supportedPrimaryOrders.add(PrimaryOrder.POLYLINE);
		supportedPrimaryOrders.add(PrimaryOrder.GLYPHINDEX);
		if (options.polygon_ellipse_orders) {
			// polygon, polygon2, ellipse, ellipse2
		}
	}

	public Collection<PrimaryOrder> getSupportedPrimaryOrders() {
		return supportedPrimaryOrders;
	}

	public void resetOrderState() {
		this.os.reset();
		os.setOrderType(PrimaryOrder.PATBLT); // Is this correct?
		return;
	}

	/**
	 * Gets the list of present fields for the given primary order.
	 *
	 * @param data The packet to read from
	 * @param controlFlags The controlFlags field for the order
	 * @param orderType The type of order
	 * @return A bitmask of what fields are present.
	 */
	private int getPresentFields(RdpPacket data, int controlFlags, PrimaryOrder orderType) {
		int ret = 0;
		int size = orderType.numFieldBytes;

		// Jankyness, to save a few bytes (even though the protocol wastes tons
		// of bytes elsewhere). See table on page 46 of [MS-RDPEGDI],
		// specifically on fieldFlags

		if ((controlFlags & PrimaryOrderFlags.ZERO_FIELD_BYTE_BIT0) != 0) {
			size--;
		}
		if ((controlFlags & PrimaryOrderFlags.ZERO_FIELD_BYTE_BIT1) != 0) {
			size -= 2;
		}

		if (size < 0) {
			LOGGER.warn("Invalid control flags/size combo for " + orderType + ": flags (" + Integer.toBinaryString(controlFlags) + ") led to size of " + size);
			size = 0;
		}

		for (int i = 0; i < size; i++) {
			int bits = data.get8();
			ret |= (bits << (i * 8));
		}

		// Sanity check
		int expectedHighestBit = (1 << (orderType.numFields - 1));
		int highestBit = Integer.highestOneBit(ret);
		if (highestBit> expectedHighestBit) {
			LOGGER.warn("More fields are set than expected; expected at max " + orderType.numFields + " fields but got 0b" + Integer.toBinaryString(ret));
		}
		return ret;
	}

	/**
	 * Process a set of orders sent by the server
	 *
	 * @param data
	 *            Packet packet containing orders
	 * @param next_packet
	 *            Offset of end of this packet (start of next)
	 * @param n_orders
	 *            Number of orders sent in this packet
	 * @throws OrderException
	 * @throws RdesktopException
	 */
	public void processOrders(RdpPacket data, int next_packet,
			int n_orders) throws OrderException, RdesktopException {

		int processed = 0;

		while (processed < n_orders) {
			int controlFlags = data.get8();

			switch (controlFlags & (RDP_ORDER_STANDARD | RDP_ORDER_SECONDARY)) {
			case RDP_ORDER_STANDARD: {
				this.processPrimaryOrders(data, controlFlags);
				break;
			}
			case RDP_ORDER_STANDARD | RDP_ORDER_SECONDARY: {
				this.processSecondaryOrders(data, controlFlags);
				break;
			}
			case RDP_ORDER_SECONDARY: {
				this.processAltSecondaryOrders(data, controlFlags);
				break;
			}
			default: {
				throw new OrderException("Order parsing failed - invalid control flags " + Integer.toBinaryString(controlFlags));
			}
			}

			processed++;
		}
		if (data.getPosition() != next_packet) {
			throw new OrderException("End not reached!");
		}
	}

	private int ROP2_S(int rop3) {
		return (rop3 & 0x0f);
	}

	private int ROP2_P(int rop3) {
		return ((rop3 & 0x3) | ((rop3 & 0x30) >> 2));
	}

	/**
	 * Register an RdesktopCanvas with this Orders object. This surface is where
	 * all drawing orders will be carried out.
	 *
	 * @param surface
	 *            Surface to register
	 */
	public void registerDrawingSurface(OrderSurface surface) {
		this.surface = surface;
		surface.registerCache(cache);
	}

	/**
	 * Handle secondary, or caching, orders
	 *
	 * @param data
	 *            Packet containing secondary order
	 * @param controlFlags
	 *            The control flags in the main packet
	 * @throws OrderException
	 * @throws RdesktopException
	 */
	private void processPrimaryOrders(RdpPacket data, int controlFlags)
			throws OrderException, RdesktopException {
		assert (controlFlags & RDP_ORDER_STANDARD) != 0;
		assert (controlFlags & RDP_ORDER_SECONDARY) == 0;
		if ((controlFlags & PrimaryOrderFlags.TYPE_CHANGE) != 0) {
			os.setOrderType(PrimaryOrder.forEncodingNumber(data.get8()));
		}

		PrimaryOrder orderType = os.getOrderType();

		int orderFlags = this.getPresentFields(data, controlFlags, orderType);

		if ((controlFlags & PrimaryOrderFlags.BOUNDS) != 0) {

			if ((controlFlags & PrimaryOrderFlags.ZERO_BOUNDS_DELTAS) == 0) {
				this.parseBounds(data, os.getBounds());
			}

			surface.setClip(os.getBounds());
		}

		boolean delta = ((controlFlags & PrimaryOrderFlags.DELTA_COORDINATES) != 0);

		LOGGER.debug("Primary order: " + orderType);
		switch (orderType) {
		case DSTBLT:
			this.processDestBlt(data, os.getDestBlt(), orderFlags, delta); break;

		case PATBLT:
			this.processPatBlt(data, os.getPatBlt(), orderFlags, delta); break;

		case SCRBLT:
			this.processScreenBlt(data, os.getScreenBlt(), orderFlags, delta); break;

		case LINETO:
			this.processLine(data, os.getLine(), orderFlags, delta); break;

		case OPAQUERECT:
			this.processRectangle(data, os.getRectangle(), orderFlags, delta); break;

		case SAVEBITMAP:
			this.processDeskSave(data, os.getDeskSave(), orderFlags, delta); break;

		case MEMBLT:
			this.processMemBlt(data, os.getMemBlt(), orderFlags, delta); break;

		case MEM3BLT:
			this.processTriBlt(data, os.getTriBlt(), orderFlags, delta); break;

		case POLYLINE:
			this.processPolyLine(data, os.getPolyLine(), orderFlags, delta); break;

		case GLYPHINDEX:
			this.processText2(data, os.getText2(), orderFlags, delta); break;

		default:
			LOGGER.warn("Unimplemented Order type " + orderType);
			return;
		}

		if ((controlFlags & PrimaryOrderFlags.BOUNDS) != 0) {
			surface.resetClip();
			LOGGER.debug("Reset clip");
		}
	}

	/**
	 * Reads an optional field, logging info for debug purposes if needed.
	 *
	 * @param name The name of the field, for debugging
	 * @param aggregate Optional place to debug log packet data.  May be null.
	 * @param id The ID of the field (starting at 0)
	 * @param present The present fields
	 * @param setter What to call if the field is present to set it
	 * @param reader What to call to read the field
	 */
	private static void readOptionalField(String name, StringBuilder aggregate, int id, int present, IntConsumer setter, IntSupplier reader) {
		boolean has = (present & (1 << id)) != 0;

		if (aggregate != null) {
			if (aggregate.length() > 0) {
				aggregate.append(", ");
			}
			aggregate.append(name);
		}

		if (has) {
			int value = reader.getAsInt();
			setter.accept(value);

			if (aggregate != null) {
				aggregate.append(" present and now set to ").append(value);
			}
		} else {
			if (aggregate != null) {
				aggregate.append(" not present");
			}
		}
	}

	/**
	 * Reads an optional field, logging info for debug purposes if needed.
	 *
	 * @param name The name of the field, for debugging
	 * @param aggregate Optional place to debug log packet data.  May be null.
	 * @param id The ID of the field (starting at 0)
	 * @param present The present fields
	 * @param setter What to call if the field is present to set it
	 * @param reader What to call to read the field
	 */
	private static <T> void readOptionalTypedField(String name, StringBuilder aggregate, int id, int present, Consumer<T> setter, Supplier<T> reader) {
		boolean has = (present & (1 << id)) != 0;

		if (aggregate != null) {
			if (aggregate.length() > 0) {
				aggregate.append(", ");
			}
			aggregate.append(name);
		}

		if (has) {
			T value = reader.get();
			setter.accept(value);

			if (aggregate != null) {
				aggregate.append(" present and now set to ");
				if (value instanceof Object[]) {
					aggregate.append(Arrays.deepToString((Object[]) value));
				} else if (value instanceof int[]) {
					aggregate.append(Arrays.toString((int[]) value));
				} else if (value instanceof byte[]) {
					aggregate.append(Arrays.toString((byte[]) value));
				} else {
					aggregate.append(value);
				}
			}
		} else {
			if (aggregate != null) {
				aggregate.append(" not present");
			}
		}
	}

	/**
	 * Process a dest blit order, and perform blit on drawing surface
	 *
	 * @param data
	 *            Packet containing description of the order
	 * @param destblt
	 *            DestBltOrder object in which to store the blit description
	 * @param present
	 *            Flags defining the information available in the packet
	 * @param delta
	 *            True if the coordinates of the blit destination are described
	 *            as relative to the source
	 */
	private void processDestBlt(RdpPacket data, DestBltOrder destblt,
			int present, boolean delta) {

		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);
		readOptionalField("left", aggregate, 0, present, destblt::setX, coordinateReader(data, destblt.getX(), delta));
		readOptionalField("top", aggregate, 1, present, destblt::setY, coordinateReader(data, destblt.getY(), delta));
		readOptionalField("width", aggregate, 2, present, destblt::setCX, coordinateReader(data, destblt.getCX(), delta));
		readOptionalField("height", aggregate, 3, present, destblt::setCY, coordinateReader(data, destblt.getCY(), delta));
		readOptionalField("rop", aggregate, 4, present, destblt::setOpcode, () -> ROP2_S(data.get8()));

		LOGGER.debug(aggregate);

		surface.drawDestBltOrder(destblt);
	}

	/**
	 * Parse data describing a pattern blit, and perform blit on drawing surface
	 *
	 * @param data
	 *            Packet containing blit data
	 * @param patblt
	 *            PatBltOrder object in which to store the blit description
	 * @param present
	 *            Flags defining the information available within the packet
	 * @param delta
	 *            True if the coordinates of the blit destination are described
	 *            as relative to the source
	 */
	private void processPatBlt(RdpPacket data, PatBltOrder patblt,
			int present, boolean delta) {
		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);

		readOptionalField("left", aggregate, 0, present, patblt::setX, coordinateReader(data, patblt.getX(), delta));
		readOptionalField("top", aggregate, 1, present, patblt::setY, coordinateReader(data, patblt.getY(), delta));
		readOptionalField("width", aggregate, 2, present, patblt::setCX, coordinateReader(data, patblt.getCX(), delta));
		readOptionalField("height", aggregate, 3, present, patblt::setCY, coordinateReader(data, patblt.getCY(), delta));
		readOptionalField("rop", aggregate, 4, present, patblt::setOpcode, () -> ROP2_P(data.get8()));
		readOptionalField("backgroundColor", aggregate, 5, present, patblt::setBackgroundColor, colorReader(data));
		readOptionalField("foregroundColor", aggregate, 6, present, patblt::setForegroundColor, colorReader(data));
		parseBrush(data, patblt.getBrush(), 7, present, aggregate);

		LOGGER.debug(aggregate);

		surface.drawPatBltOrder(patblt);
	}

	/**
	 * Parse data describing a screen blit, and perform blit on drawing surface
	 *
	 * @param data
	 *            Packet containing blit data
	 * @param screenblt
	 *            ScreenBltOrder object in which to store blit description
	 * @param present
	 *            Flags defining the information available within the packet
	 * @param delta
	 *            True if the coordinates of the blit destination are described
	 *            as relative to the source
	 */
	private void processScreenBlt(RdpPacket data,
			ScreenBltOrder screenblt, int present, boolean delta) {
		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);

		readOptionalField("left", aggregate, 0, present, screenblt::setX, coordinateReader(data, screenblt.getX(), delta));
		readOptionalField("top", aggregate, 1, present, screenblt::setY, coordinateReader(data, screenblt.getY(), delta));
		readOptionalField("width", aggregate, 2, present, screenblt::setCX, coordinateReader(data, screenblt.getCX(), delta));
		readOptionalField("height", aggregate, 3, present, screenblt::setCY, coordinateReader(data, screenblt.getCY(), delta));
		readOptionalField("rop", aggregate, 4, present, screenblt::setOpcode, () -> ROP2_S(data.get8()));
		readOptionalField("srcX", aggregate, 5, present, screenblt::setSrcX, coordinateReader(data, screenblt.getSrcX(), delta));
		readOptionalField("srcY", aggregate, 6, present, screenblt::setSrcY, coordinateReader(data, screenblt.getSrcY(), delta));

		LOGGER.debug(aggregate);

		surface.drawScreenBltOrder(screenblt);
	}

	/**
	 * Parse data describing a line order, and draw line on drawing surface
	 *
	 * @param data
	 *            Packet containing line order data
	 * @param line
	 *            LineOrder object describing the line drawing operation
	 * @param present
	 *            Flags defining the information available within the packet
	 * @param delta
	 *            True if the coordinates of the end of the line are defined as
	 *            relative to the start
	 */
	private void processLine(RdpPacket data, LineOrder line,
			int present, boolean delta) {
		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);

		readOptionalField("backgroundMixMode", aggregate, 0, present, line::setMixmode, data::getLittleEndian16);
		readOptionalField("startX", aggregate, 1, present, line::setStartX, coordinateReader(data, line.getStartX(), delta));
		readOptionalField("startY", aggregate, 2, present, line::setStartY, coordinateReader(data, line.getStartY(), delta));
		readOptionalField("endX", aggregate, 3, present, line::setEndX, coordinateReader(data, line.getEndX(), delta));
		readOptionalField("endY", aggregate, 4, present, line::setEndY, coordinateReader(data, line.getEndY(), delta));
		readOptionalField("backColor", aggregate, 5, present, line::setBackgroundColor, colorReader(data));
		readOptionalField("rop2", aggregate, 6, present, line::setOpcode, data::get8);

		parsePen(data, line.getPen(), 7, present, aggregate);

		LOGGER.debug(aggregate);

		if (line.getOpcode() < 0x01 || line.getOpcode() > 0x10) {
			LOGGER.warn("bad ROP2 0x" + Integer.toHexString(line.getOpcode()));
			return;
		}

		surface.drawLineOrder(line);
	}

	/**
	 * Parse data describing a rectangle order, and draw the rectangle to the
	 * drawing surface
	 *
	 * @param data
	 *            Packet containing rectangle order
	 * @param rect
	 *            RectangleOrder object in which to store order description
	 * @param present
	 *            Flags defining information available in packet
	 * @param delta
	 *            True if the rectangle is described as (x,y,width,height), as
	 *            opposed to (x1,y1,x2,y2)
	 */
	private void processRectangle(RdpPacket data,
			RectangleOrder rect, int present, boolean delta) {
		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);

		readOptionalField("left", aggregate, 0, present, rect::setX, coordinateReader(data, rect.getX(), delta));
		readOptionalField("top", aggregate, 1, present, rect::setY, coordinateReader(data, rect.getY(), delta));
		readOptionalField("width", aggregate, 2, present, rect::setCX, coordinateReader(data, rect.getCX(), delta));
		readOptionalField("height", aggregate, 3, present, rect::setCY, coordinateReader(data, rect.getCY(), delta));

		// This splits colors into 3 portions
		// TODO: palette indexes
		readOptionalField("red", aggregate, 4, present, rect::setR, data::get8);
		readOptionalField("green", aggregate, 5, present, rect::setG, data::get8);
		readOptionalField("blue", aggregate, 6, present, rect::setB, data::get8);

		LOGGER.debug(aggregate);

		surface.drawRectangleOrder(rect);
	}

	/**
	 * Parse data describing a desktop save order, either saving the desktop to
	 * cache, or drawing a section to screen
	 *
	 * @param data
	 *            Packet containing desktop save order
	 * @param desksave
	 *            DeskSaveOrder object in which to store order description
	 * @param present
	 *            Flags defining information available within the packet
	 * @param delta
	 *            True if destination coordinates are described as relative to
	 *            the source
	 * @throws RdesktopException
	 */
	private void processDeskSave(RdpPacket data,
			DeskSaveOrder desksave, int present, boolean delta)
					throws RdesktopException {
		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);

		readOptionalField("savedBitmapPosition", aggregate, 0, present, desksave::setOffset, data::getLittleEndian32);
		readOptionalField("left", aggregate, 1, present, desksave::setLeft, coordinateReader(data, desksave.getLeft(), delta));
		readOptionalField("top", aggregate, 2, present, desksave::setTop, coordinateReader(data, desksave.getTop(), delta));
		readOptionalField("right", aggregate, 3, present, desksave::setRight, coordinateReader(data, desksave.getRight(), delta));
		readOptionalField("bottom", aggregate, 4, present, desksave::setBottom, coordinateReader(data, desksave.getBottom(), delta));
		readOptionalField("operation", aggregate, 5, present, desksave::setAction, data::get8);

		LOGGER.debug(aggregate);

		// Perform it
		int width = desksave.getRight() - desksave.getLeft() + 1;
		int height = desksave.getBottom() - desksave.getTop() + 1;

		if (desksave.getAction() == 0) {
			int[] pixel = surface.getImage(desksave.getLeft(), desksave
					.getTop(), width, height);
			cache.putDesktop(desksave.getOffset(), width, height, pixel);
		} else {
			int[] pixel = cache.getDesktopInt(desksave.getOffset(),
					width, height);
			surface.putImage(desksave.getLeft(), desksave.getTop(), width,
					height, pixel);
		}
	}

	/**
	 * Process data describing a memory blit, and perform blit on drawing
	 * surface
	 *
	 * @param data
	 *            Packet containing mem blit order
	 * @param memblt
	 *            MemBltOrder object in which to store description of blit
	 * @param present
	 *            Flags defining information available in packet
	 * @param delta
	 *            True if destination coordinates are described as relative to
	 *            the source
	 */
	private void processMemBlt(RdpPacket data, MemBltOrder memblt,
			int present, boolean delta) {
		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);

		// 2 values in the same packet field (with the same ID)
		readOptionalField("cacheID", aggregate, 0, present, memblt::setCacheID, data::get8);
		readOptionalField("colorTable", aggregate, 0, present, memblt::setColorTable, data::get8);
		readOptionalField("left", aggregate, 1, present, memblt::setX, coordinateReader(data, memblt.getX(), delta));
		readOptionalField("top", aggregate, 2, present, memblt::setY, coordinateReader(data, memblt.getY(), delta));
		readOptionalField("width", aggregate, 3, present, memblt::setCX, coordinateReader(data, memblt.getCX(), delta));
		readOptionalField("height", aggregate, 4, present, memblt::setCY, coordinateReader(data, memblt.getCY(), delta));
		readOptionalField("rop", aggregate, 5, present, memblt::setOpcode, () -> ROP2_S(data.get8()));
		readOptionalField("srcX", aggregate, 6, present, memblt::setSrcX, coordinateReader(data, memblt.getSrcX(), delta));
		readOptionalField("srcY", aggregate, 7, present, memblt::setSrcY, coordinateReader(data, memblt.getSrcY(), delta));
		readOptionalField("cacheIndex", aggregate, 8, present, memblt::setCacheIDX, data::getLittleEndian16);

		LOGGER.debug(aggregate);

		surface.drawMemBltOrder(memblt);
	}

	/**
	 * Parse data describing a tri blit order, and perform blit on drawing
	 * surface
	 *
	 * @param data
	 *            Packet containing tri blit order
	 * @param triblt
	 *            TriBltOrder object in which to store blit description
	 * @param present
	 *            Flags defining information available in packet
	 * @param delta
	 *            True if destination coordinates are described as relative to
	 *            the source
	 */
	private void processTriBlt(RdpPacket data, TriBltOrder triblt,
			int present, boolean delta) {
		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);

		readOptionalField("cacheID", aggregate, 0, present, triblt::setCacheID, data::get8);
		readOptionalField("colorTable", aggregate, 0, present, triblt::setColorTable, data::get8);
		readOptionalField("left", aggregate, 1, present, triblt::setX, coordinateReader(data, triblt.getX(), delta));
		readOptionalField("top", aggregate, 2, present, triblt::setY, coordinateReader(data, triblt.getY(), delta));
		readOptionalField("width", aggregate, 3, present, triblt::setCX, coordinateReader(data, triblt.getCX(), delta));
		readOptionalField("height", aggregate, 4, present, triblt::setCY, coordinateReader(data, triblt.getCY(), delta));
		readOptionalField("rop", aggregate, 5, present, triblt::setOpcode, () -> ROP2_S(data.get8()));
		readOptionalField("srcX", aggregate, 6, present, triblt::setSrcX, coordinateReader(data, triblt.getSrcX(), delta));
		readOptionalField("srcY", aggregate, 7, present, triblt::setSrcY, coordinateReader(data, triblt.getSrcY(), delta));
		parseBrush(data, triblt.getBrush(), 8, present, aggregate);
		readOptionalField("cacheIndex", aggregate, 15, present, triblt::setCacheIDX, data::getLittleEndian16);

		LOGGER.debug(aggregate);

		surface.drawTriBltOrder(triblt);
	}

	/**
	 * Parse data describing a multi-line order, and draw to registered surface
	 *
	 * @param data
	 *            Packet containing polyline order
	 * @param polyline
	 *            PolyLineOrder object in which to store order description
	 * @param present
	 *            Flags defining information available in packet
	 * @param delta
	 *            True if each set of coordinates is described relative to
	 *            previous set
	 */
	private void processPolyLine(RdpPacket data,
			PolyLineOrder polyline, int present, boolean delta) {
		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);

		readOptionalField("x", aggregate, 0, present, polyline::setX, coordinateReader(data, polyline.getY(), delta));
		readOptionalField("y", aggregate, 1, present, polyline::setY, coordinateReader(data, polyline.getY(), delta));
		readOptionalField("rop2", aggregate, 2, present, polyline::setOpcode, data::get8);
		readOptionalField("brushCacheEntry (unused, should be 0)", aggregate, 3, present, n -> { assert n == 0; /* see footnote 3 on [MS-RDPEGDI] */}, data::getLittleEndian16);
		readOptionalField("penColor", aggregate, 4, present, polyline::setForegroundColor, colorReader(data));
		readOptionalField("numLines", aggregate, 5, present, polyline::setLines, data::get8);
		// Multi-byte structure
		readOptionalField("deltaListSize", aggregate, 6, present, polyline::setDataSize, data::get8);
		readOptionalTypedField("deltaList", aggregate, 6, present, polyline::setData, () -> {
			byte[] databytes = new byte[polyline.getDataSize()];
			for (int i = 0; i < databytes.length; i++) {
				databytes[i] = (byte) data.get8();
			}
			return databytes;
		});

		LOGGER.debug(aggregate);

		surface.drawPolyLineOrder(polyline);
	}

	/**
	 * Process a text2 order and output to drawing surface
	 *
	 * @param data
	 *            Packet containing text2 order
	 * @param text2
	 *            Text2Order object in which to store order description
	 * @param present
	 *            Flags defining information available in packet
	 * @param delta
	 *            Unused
	 * @throws RdesktopException
	 */
	private void processText2(RdpPacket data, Text2Order text2,
			int present, boolean delta) throws RdesktopException {
		StringBuilder aggregate = (LOGGER.isDebugEnabled() ? new StringBuilder() : null);

		readOptionalField("cacheID", aggregate, 0, present, text2::setFont, data::get8);
		readOptionalField("accelerationFlags", aggregate, 1, present, text2::setFlags, data::get8);
		readOptionalField("ulCharInc", aggregate, 2, present, text2::setFixedWidthAdvance, data::get8);
		// ... because words have meanings ...
		// whether or not the opaque rectangle is redundant. Redundant, in this
		// context, means that the text background is transparent.
		readOptionalField("opaqueRectangleRedundant", aggregate, 3, present, text2::setMixmode, data::get8);
		readOptionalField("backColor", aggregate, 4, present, text2::setBackgroundColor, colorReader(data));
		readOptionalField("foreColor", aggregate, 5, present, text2::setForegroundColor, colorReader(data));
		readOptionalField("leftClip", aggregate, 6, present, text2::setClipLeft, data::getLittleEndian16);
		readOptionalField("topClip", aggregate, 7, present, text2::setClipTop, data::getLittleEndian16);
		readOptionalField("rightClip", aggregate, 8, present, text2::setClipRight, data::getLittleEndian16);
		readOptionalField("bottomClip", aggregate, 9, present, text2::setClipBottom, data::getLittleEndian16);
		readOptionalField("leftBox", aggregate, 10, present, text2::setBoxLeft, data::getLittleEndian16);
		readOptionalField("topBox", aggregate, 11, present, text2::setBoxTop, data::getLittleEndian16);
		readOptionalField("rightBox", aggregate, 12, present, text2::setBoxRight, data::getLittleEndian16);
		readOptionalField("bottomBox", aggregate, 13, present, text2::setBoxBottom, data::getLittleEndian16);
		// TODO: handle the brush correctly
		parseBrush(data, new Brush(), 14, present, aggregate);
		readOptionalField("x", aggregate, 19, present, text2::setX, data::getLittleEndian16);
		readOptionalField("y", aggregate, 20, present, text2::setY, data::getLittleEndian16);

		// Multi-byte structure
		readOptionalField("textLength", aggregate, 21, present, text2::setLength, data::get8);
		readOptionalTypedField("text", aggregate, 21, present, text2::setText, () -> {
			byte[] text = new byte[text2.getLength()];
			data.copyToByteArray(text, 0, data.getPosition(), text.length);
			data.incrementPosition(text.length);
			return text;
		});

		LOGGER.debug(aggregate);

		this.drawText(text2, text2.getClipRight() - text2.getClipLeft(), text2
				.getClipBottom()
				- text2.getClipTop(), text2.getBoxRight() - text2.getBoxLeft(),
				text2.getBoxBottom() - text2.getBoxTop());

	}

	/**
	 * Parse a description for a bounding box
	 *
	 * @param data
	 *            Packet containing order defining bounding box
	 * @param bounds
	 *            BoundsOrder object in which to store description of bounds
	 * @throws OrderException
	 */
	private void parseBounds(RdpPacket data, BoundsOrder bounds)
			throws OrderException {
		int present = data.get8();

		// From pages 46 and 47 of [MS-RDPEGDI]:

		// The description byte MUST contain a TS_BOUND_XXX or
		// TS_BOUND_DELTA_XXX flag to describe each of the encoded bounds that
		// are present.
		// If for a given component a TS_BOUND_XXX or TS_BOUND_DELTA_XXX flag is
		// not present, the component value is the same as the last one used,
		// and no value is included in the encoded bounds. If both the
		// TS_BOUND_XXX and TS_BOUND_DELTA_XXX flags are present, the
		// TS_BOUND_XXX flag is ignored. Hence, to avoid parsing errors, only
		// one flag MUST be used to describe the format of a given encoded
		// bound.

		// As such, I warn if there's both, and do nothing if there's neither (as
		// that is a valid condition)

		if ((present & PrimaryOrderFlags.BOUND_DELTA_LEFT) != 0) {
			bounds.setLeft((short) (bounds.getLeft() + (byte) data.get8()));
			if ((present & PrimaryOrderFlags.BOUND_LEFT) != 0) {
				LOGGER.warn("Both BOUND_LEFT and BOUND_DELTA_LEFT were set! (0b" + Integer.toBinaryString(present) + ")");
			}
		} else if ((present & PrimaryOrderFlags.BOUND_LEFT) != 0) {
			bounds.setLeft((short) (data.getLittleEndian16()));
		}

		if ((present & PrimaryOrderFlags.BOUND_DELTA_TOP) != 0) {
			bounds.setTop((short) (bounds.getTop() + (byte) data.get8()));
			if ((present & PrimaryOrderFlags.BOUND_TOP) != 0) {
				LOGGER.warn("Both BOUND_TOP and BOUND_DELTA_TOP were set! (0b" + Integer.toBinaryString(present) + ")");
			}
		} else if ((present & PrimaryOrderFlags.BOUND_TOP) != 0) {
			bounds.setTop((short) (data.getLittleEndian16()));
		}

		if ((present & PrimaryOrderFlags.BOUND_DELTA_RIGHT) != 0) {
			bounds.setRight((short) (bounds.getRight() + (byte) data.get8()));
			if ((present & PrimaryOrderFlags.BOUND_RIGHT) != 0) {
				LOGGER.warn("Both BOUND_RIGHT and BOUND_DELTA_RIGHT were set! (0b" + Integer.toBinaryString(present) + ")");
			}
		} else if ((present & PrimaryOrderFlags.BOUND_RIGHT) != 0) {
			bounds.setRight((short) (data.getLittleEndian16()));
		}

		if ((present & PrimaryOrderFlags.BOUND_DELTA_BOTTOM) != 0) {
			bounds.setBottom((short) (bounds.getBottom() + (byte) data.get8()));
			if ((present & PrimaryOrderFlags.BOUND_BOTTOM) != 0) {
				LOGGER.warn("Both BOUND_BOTTOM and BOUND_DELTA_BOTTOM were set! (0b" + Integer.toBinaryString(present) + ")");
			}
		} else if ((present & PrimaryOrderFlags.BOUND_BOTTOM) != 0) {
			bounds.setBottom((short) (data.getLittleEndian16()));
		}

		if (data.getPosition() > data.getEnd()) {
			throw new OrderException("Too far!");
		}
	}

	/**
	 * Handle secondary, or caching, orders
	 *
	 * @param data
	 *            Packet containing secondary order
	 * @param controlFlags
	 *            The control flags in the main packet
	 * @throws OrderException
	 * @throws RdesktopException
	 */
	private void processSecondaryOrders(RdpPacket data, int controlFlags)
			throws OrderException, RdesktopException {
		assert (controlFlags & RDP_ORDER_STANDARD) != 0;
		assert (controlFlags & RDP_ORDER_SECONDARY) != 0;

		int length = 0;
		int flags = 0;
		int next_order = 0;

		length = data.getLittleEndian16();
		flags = data.getLittleEndian16();
		SecondaryOrder type = SecondaryOrder.forId(data.get8());

		next_order = data.getPosition() + length + 7;

		LOGGER.debug("Secondary order: " + type);
		switch (type) {
		case BITMAP_UNCOMPRESSED:
			this.processRawBitmapCache(data);
			break;

		case COLOR_TABLE:
			this.processColorCache(data);
			break;

		case BITMAP_COMPRESSED:
			this.processBitmapCache(data);
			break;

		case GLYPH:
			this.processFontCache(data);
			break;

		case BITMAP_UNCOMPRESSED_REV2:
			try {
				this.processBitmapCache2(data, flags, false);
			} catch (IOException e) {
				throw new RdesktopException(e.getMessage(), e);
			} /* uncompressed */
			break;

		case BITMAP_COMPRESSED_REV2:
			try {
				this.processBitmapCache2(data, flags, true);
			} catch (IOException e) {
				throw new RdesktopException(e.getMessage(), e);
			} /* compressed */
			break;

		default:
			LOGGER.warn("Unimplemented 2ry Order type " + type);
		}

		data.setPosition(next_order);
	}

	/**
	 * Process a raw bitmap and store it in the bitmap cache
	 *
	 * @param data
	 *            Packet containing raw bitmap data
	 * @throws RdesktopException
	 */
	private void processRawBitmapCache(RdpPacket data)
			throws RdesktopException {
		int cache_id = data.get8();
		data.incrementPosition(1); // pad
		int width = data.get8();
		int height = data.get8();
		int bpp = data.get8();
		int Bpp = (bpp + 7) / 8;
		int bufsize = data.getLittleEndian16();
		int cache_idx = data.getLittleEndian16();
		int pdata = data.getPosition();
		data.incrementPosition(bufsize);

		byte[] inverted = new byte[width * height * Bpp];
		int pinverted = (height - 1) * (width * Bpp);
		for (int y = 0; y < height; y++) {
			data.copyToByteArray(inverted, pinverted, pdata, width * Bpp);
			// data.copyToByteArray(inverted, (height - y - 1) * (width * Bpp),
			// y * (width * Bpp), width*Bpp);
			pinverted -= width * Bpp;
			pdata += width * Bpp;
		}

		cache.putBitmap(cache_id, cache_idx, new Bitmap(Bitmap.convertImage(options,
				inverted, Bpp), width, height, 0, 0), 0);
	}

	/**
	 * Process and store details of a colour cache
	 *
	 * @param data
	 *            Packet containing cache information
	 * @throws RdesktopException
	 */
	private void processColorCache(RdpPacket data)
			throws RdesktopException {
		byte[] palette = null;

		byte[] red = null;
		byte[] green = null;
		byte[] blue = null;
		int j = 0;

		int cache_id = data.get8();
		int n_colors = data.getLittleEndian16(); // Number of Colors in
		// Palette

		palette = new byte[n_colors * 4];
		red = new byte[n_colors];
		green = new byte[n_colors];
		blue = new byte[n_colors];
		data.copyToByteArray(palette, 0, data.getPosition(), palette.length);
		data.incrementPosition(palette.length);
		for (int i = 0; i < n_colors; i++) {
			blue[i] = palette[j];
			green[i] = palette[j + 1];
			red[i] = palette[j + 2];
			// palette[j+3] is pad
			j += 4;
		}
		IndexColorModel cm = new IndexColorModel(8, n_colors, red, green, blue);
		cache.put_colourmap(cache_id, cm);
		// surface.registerPalette(cm);
	}

	/**
	 * Process a compressed bitmap and store in the bitmap cache
	 *
	 * @param data
	 *            Packet containing compressed bitmap
	 * @throws RdesktopException
	 */
	private void processBitmapCache(RdpPacket data)
			throws RdesktopException {
		int size;

		int cache_id = data.get8();
		data.get8(); // pad
		int width = data.get8();
		int height = data.get8();
		int bpp = data.get8();
		int Bpp = (bpp + 7) / 8;
		data.getLittleEndian16(); // bufsize
		int cache_idx = data.getLittleEndian16();

		/*
		 * data.incrementPosition(2); // pad int size =
		 * data.getLittleEndian16(); data.incrementPosition(4); // row_size,
		 * final_size
		 */

		if (options.use_rdp5) {

			/* Begin compressedBitmapData */
			data.getLittleEndian16(); // in_uint16_le(s, pad2); /* pad */
			size = data.getLittleEndian16(); // in_uint16_le(s, size);
			data.getLittleEndian16(); // in_uint16_le(s, row_size);
			data.getLittleEndian16(); // in_uint16_le(s, final_size);

		} else {
			data.incrementPosition(2); // pad
			size = data.getLittleEndian16();
			data.getLittleEndian16(); // in_uint16_le(s, row_size);
			data.getLittleEndian16(); // in_uint16_le(s,final_size);
			// this is what's in rdesktop, but doesn't seem to work
			// size = bufsize;
		}

		// logger.info("BMPCACHE(cx=" + width + ",cy=" + height + ",id=" +
		// cache_id + ",idx=" + cache_idx + ",bpp=" + bpp + ",size=" + size +
		// ",pad1=" + pad1 + ",bufsize=" + bufsize + ",pad2=" + pad2 + ",rs=" +
		// row_size + ",fs=" + final_size + ")");

		if (Bpp == 1) {
			byte[] pixel = Bitmap.decompress(options, width, height, size, data, Bpp);
			if (pixel != null) {
				cache.putBitmap(cache_id, cache_idx, new Bitmap(Bitmap
						.convertImage(options, pixel, Bpp), width, height, 0, 0), 0);
			} else {
				LOGGER.warn("Failed to decompress bitmap");
			}
		} else {
			int[] pixel = Bitmap.decompressInt(options, width, height, size, data, Bpp);
			if (pixel != null) {
				cache.putBitmap(cache_id, cache_idx, new Bitmap(pixel, width,
						height, 0, 0), 0);
			} else {
				LOGGER.warn("Failed to decompress bitmap");
			}
		}
	}

	/**
	 * Process a bitmap cache v2 order, storing a bitmap in the main cache, and
	 * the persistant cache if so required
	 *
	 * @param data
	 *            Packet containing order and bitmap data
	 * @param flags
	 *            Set of flags defining mode of order
	 * @param compressed
	 *            True if bitmap data is compressed
	 * @throws RdesktopException
	 * @throws IOException
	 */
	private void processBitmapCache2(RdpPacket data, int flags,
			boolean compressed) throws RdesktopException, IOException {
		Bitmap bitmap;
		int y;
		int cache_id, cache_idx_low, width, height, Bpp;
		int cache_idx, bufsize;
		byte[] bmpdata, bitmap_id;

		bitmap_id = new byte[8]; /* prevent compiler warning */
		cache_id = flags & ID_MASK;
		Bpp = ((flags & MODE_MASK) >> MODE_SHIFT) - 2;
		Bpp = options.Bpp;
		if ((flags & PERSIST) != 0) {
			bitmap_id = new byte[8];
			data.copyToByteArray(bitmap_id, 0, data.getPosition(), 8);
		}

		if ((flags & SQUARE) != 0) {
			width = data.get8(); // in_uint8(s, width);
			height = width;
		} else {
			width = data.get8(); // in_uint8(s, width);
			height = data.get8(); // in_uint8(s, height);
		}

		bufsize = data.getBigEndian16(); // in_uint16_be(s, bufsize);
		bufsize &= BUFSIZE_MASK;
		cache_idx = data.get8(); // in_uint8(s, cache_idx);

		if ((cache_idx & LONG_FORMAT) != 0) {
			cache_idx_low = data.get8(); // in_uint8(s, cache_idx_low);
			cache_idx = ((cache_idx ^ LONG_FORMAT) << 8) + cache_idx_low;
		}

		// in_uint8p(s, data, bufsize);

		LOGGER.info("BMPCACHE2(compr=" + compressed + ",flags=" + flags
				+ ",cx=" + width + ",cy=" + height + ",id=" + cache_id
				+ ",idx=" + cache_idx + ",Bpp=" + Bpp + ",bs=" + bufsize + ")");

		bmpdata = new byte[width * height * Bpp];
		int[] bmpdataInt = new int[width * height];

		if (compressed) {
			if (Bpp == 1) {
				bmpdataInt = Bitmap.convertImage(options, Bitmap.decompress(options, width,
						height, bufsize, data, Bpp), Bpp);
			} else {
				bmpdataInt = Bitmap.decompressInt(options, width, height, bufsize, data,
						Bpp);
			}

			if (bmpdataInt == null) {
				LOGGER.debug("Failed to decompress bitmap data");
				// xfree(bmpdata);
				return;
			}
			bitmap = new Bitmap(bmpdataInt, width, height, 0, 0);
		} else {
			for (y = 0; y < height; y++) {
				data.copyToByteArray(bmpdata, y * (width * Bpp),
						(height - y - 1) * (width * Bpp), width * Bpp);
			}

			bitmap = new Bitmap(Bitmap.convertImage(options, bmpdata, Bpp), width,
					height, 0, 0);
		}

		cache.putBitmap(cache_id, cache_idx, bitmap, 0);
		if ((flags & PERSIST) != 0) {
			cache.pstCache.pstcache_put_bitmap(cache_id, cache_idx, bitmap_id,
					width, height, width * height * Bpp, bmpdata);
		}
	}

	/**
	 * Process a font caching order, and store font in the cache
	 *
	 * @param data
	 *            Packet containing font cache order, with data for a series of
	 *            glyphs representing a font
	 * @throws RdesktopException
	 */
	private void processFontCache(RdpPacket data)
			throws RdesktopException {
		Glyph glyph = null;

		int font = 0, nglyphs = 0;
		int character = 0, offset = 0, baseline = 0, width = 0, height = 0;
		int datasize = 0;
		byte[] fontdata = null;

		font = data.get8();
		nglyphs = data.get8();

		for (int i = 0; i < nglyphs; i++) {
			character = data.getLittleEndian16();
			offset = data.getLittleEndian16();
			baseline = data.getLittleEndian16();
			width = data.getLittleEndian16();
			height = data.getLittleEndian16();
			datasize = (height * ((width + 7) / 8) + 3) & ~3;
			fontdata = new byte[datasize];

			data.copyToByteArray(fontdata, 0, data.getPosition(), datasize);
			data.incrementPosition(datasize);
			glyph = new Glyph(font, character, offset, baseline, width, height,
					fontdata);
			cache.putFont(glyph);
		}
	}

	/**
	 * Handle alternate secondary orders
	 *
	 * @param data
	 *            Packet containing alternate secondary order
	 * @param controlFlags
	 *            The control flags in the main packet
	 * @throws OrderException
	 * @throws RdesktopException
	 */
	private void processAltSecondaryOrders(RdpPacket data,
			int controlFlags) throws RdesktopException, OrderException {
		assert (controlFlags & RDP_ORDER_STANDARD) == 0;
		assert (controlFlags & RDP_ORDER_SECONDARY) != 0;

		AltSecondaryOrder order = AltSecondaryOrder.forPacketId(controlFlags);
		LOGGER.debug("Altsec order: " + order);
		throw new OrderException("Alternate secondary orders aren't implemented");
	}

	/**
	 * Returns a method that retrieves a coordinate from a packet and return as
	 * an absolute integer coordinate.
	 *
	 * @param data
	 *            Packet containing coordinate at current read position
	 * @param current
	 *            Current coordinate
	 * @param delta
	 *            True if coordinate being read should be taken as relative to
	 *            offset coordinate, false if absolute
	 * @return Callback to the new value
	 * @see [MS-RDPEGDI] section 2.2.2.2.1.1.1.1
	 */
	private static IntSupplier coordinateReader(final RdpPacket data,
			final int current, final boolean delta) {
		return (() -> {
			if (delta) {
				byte change = (byte) data.get8(); // note: cast to byte for sign
				return (short) (current + change); // note: again, casting for sign
			} else {
				return (short) data.getLittleEndian16(); // note: again, casting for sign
			}
		});
	}

	/**
	 * Read a colour value from a packet
	 *
	 * @param data
	 *            Packet containing colour value at current read position
	 * @return Callback to read the int value
	 */
	private static IntSupplier colorReader(RdpPacket data) {
		return (() -> {
			// TODO: This doesn't handle lower bits per pixels
			int color;
			color = data.get8();
			color |= data.get8() << 8;
			color |= data.get8() << 16;
			return color;
		});
	}

	/**
	 * Parse data defining a brush and store brush information
	 *
	 * @param data
	 *            Packet containing brush data
	 * @param brush
	 *            Brush object in which to store the brush description
	 * @param startAt
	 *            First flag index to start at within present
	 * @param present
	 *            Flags defining the information available within the packet
	 * @param aggregate
	 *            Debug log builder
	 */
	private static void parseBrush(RdpPacket data, Brush brush, int startAt, int present, StringBuilder aggregate) {
		readOptionalField("brushX", aggregate, startAt + 0, present, brush::setXOrigin, data::get8);
		readOptionalField("brushY", aggregate, startAt + 1, present, brush::setYOrigin, data::get8);
		readOptionalField("brushStyle", aggregate, startAt + 2, present, brush::setStyle, data::get8);
		// TODO: cached brushes
		// This is a bit of a mess, because there's 2 arrays
		readOptionalField("brushHatch", aggregate, startAt + 3, present, brush::setPatternHatch, data::get8);
		readOptionalTypedField("brushExtra", aggregate, startAt + 4, present, brush::setPatternExtra, () -> new int[] {data.get8(), data.get8(), data.get8(), data.get8(), data.get8(), data.get8(), data.get8()}); // read 7
	}

	/**
	 * Parse a pen definition
	 *
	 * @param data
	 *            Packet containing pen description at current read position
	 * @param pen
	 *            Pen object in which to store pen description
	 * @param startAt
	 *            First flag index to start at within present
	 * @param present
	 *            Flags defining the information available within the packet
	 * @param aggregate
	 *            Debug log builder
	 */
	private static void parsePen(RdpPacket data, Pen pen, int startAt, int present, StringBuilder aggregate) {
		readOptionalField("penStyle", aggregate, startAt + 0, present, pen::setStyle, data::get8);
		readOptionalField("penWidth", aggregate, startAt + 0, present, pen::setWidth, data::get8);
		readOptionalField("penColor", aggregate, startAt + 0, present, pen::setColor, colorReader(data));
	}

	/**
	 * Set current cache
	 *
	 * @param cache
	 *            Cache object to set as current global cache
	 */
	public void registerCache(Cache cache) {
		this.cache = cache;
	}

	/**
	 * Interpret an integer as a 16-bit two's complement number, based on its
	 * binary representation
	 *
	 * @param val
	 *            Integer interpretation of binary number
	 * @return 16-bit two's complement value of input
	 */
	private int twosComplement16(int val) {
		return ((val & 0x8000) != 0) ? -((~val & 0xFFFF) + 1) : val;
	}

	/**
	 * Draw a text2 order to the drawing surface
	 *
	 * @param text2
	 *            Text2Order describing text to be drawn
	 * @param clipcx
	 *            Width of clipping area
	 * @param clipcy
	 *            Height of clipping area
	 * @param boxcx
	 *            Width of bounding box (to draw if > 1)
	 * @param boxcy
	 *            Height of bounding box (to draw if boxcx > 1)
	 * @throws RdesktopException
	 */
	private void drawText(Text2Order text2, int clipcx, int clipcy, int boxcx,
			int boxcy) throws RdesktopException {
		byte[] text = text2.getText();
		DataBlob entry = null;
		Glyph glyph = null;
		int offset = 0;
		int ptext = 0;
		int length = text2.getLength();
		int x = text2.getX();
		int y = text2.getY();

		if (boxcx > 1) {
			surface.fillRectangle(text2.getBoxLeft(), text2.getBoxTop(), boxcx,
					boxcy, text2.getBackgroundColor());
		} else if (text2.getMixmode() == MIX_OPAQUE) {
			surface.fillRectangle(text2.getClipLeft(), text2.getClipTop(),
					clipcx, clipcy, text2.getBackgroundColor());
		}

		/*
		 * logger.debug("X: " + text2.getX() + " Y: " + text2.getY() + " Left
		 * Clip: " + text2.getClipLeft() + " Top Clip: " + text2.getClipTop() + "
		 * Right Clip: " + text2.getClipRight() + " Bottom Clip: " +
		 * text2.getClipBottom() + " Left Box: " + text2.getBoxLeft() + " Top
		 * Box: " + text2.getBoxTop() + " Right Box: " + text2.getBoxRight() + "
		 * Bottom Box: " + text2.getBoxBottom() + " Foreground Color: " +
		 * text2.getForegroundColor() + " Background Color: " +
		 * text2.getBackgroundColor() + " Font: " + text2.getFont() + " Flags: " +
		 * text2.getFlags() + " Mixmode: " + text2.getMixmode() + " Unknown: " +
		 * text2.getUnknown() + " Length: " + text2.getLength());
		 */
		for (int i = 0; i < length;) {
			switch (text[ptext + i] & 0x000000ff) {
			case (0xff):
				if (i + 2 < length) {
					byte[] data = new byte[text[ptext + i + 2] & 0x000000ff];
					System.arraycopy(text, ptext, data, 0,
							text[ptext + i + 2] & 0x000000ff);
					DataBlob db = new DataBlob(
							text[ptext + i + 2] & 0x000000ff, data);
					cache.putText(text[ptext + i + 1] & 0x000000ff, db);
				} else {
					throw new RdesktopException();
				}
			length -= i + 3;
			ptext = i + 3;
			i = 0;
			break;

			case (0xfe):
				entry = cache.getText(text[ptext + i + 1] & 0x000000ff);
			if (entry != null) {
				if ((entry.getData()[1] == 0)
						&& ((text2.getFlags() & TEXT2_IMPLICIT_X) == 0)) {
					if ((text2.getFlags() & 0x04) != 0) {
						y += text[ptext + i + 2] & 0x000000ff;
					} else {
						x += text[ptext + i + 2] & 0x000000ff;
					}
				}
			}
			if (i + 2 < length) {
				i += 3;
			} else {
				i += 2;
			}
			length -= i;
			ptext = i;
			i = 0;
			// break;

			byte[] data = entry.getData();
			for (int j = 0; j < entry.getSize(); j++) {
				glyph = cache
						.getFont(text2.getFont(), data[j] & 0x000000ff);
				if ((text2.getFlags() & TEXT2_IMPLICIT_X) == 0) {
					offset = data[++j] & 0x000000ff;
					if ((offset & 0x80) != 0) {
						if ((text2.getFlags() & TEXT2_VERTICAL) != 0) {
							int var = this
									.twosComplement16((data[j + 1] & 0xff)
											| ((data[j + 2] & 0xff) << 8));
							y += var;
							j += 2;
						} else {
							int var = this
									.twosComplement16((data[j + 1] & 0xff)
											| ((data[j + 2] & 0xff) << 8));
							x += var;
							j += 2;
						}
					} else {
						if ((text2.getFlags() & TEXT2_VERTICAL) != 0) {
							y += offset;
						} else {
							x += offset;
						}
					}
				}
				if (glyph != null) {
					// if((text2.getFlags() & TEXT2_VERTICAL) != 0)
					// logger.info("Drawing glyph: (" + (x +
					// (short)glyph.getOffset()) + ", " + (y +
					// (short)glyph.getBaseLine()) + ")" );
					surface.drawGlyph(text2.getMixmode(), x
							+ (short) glyph.getOffset(), y
							+ (short) glyph.getBaseLine(),
							glyph.getWidth(), glyph.getHeight(), glyph
							.getFontData(), text2
							.getBackgroundColor(), text2
							.getForegroundColor());

					if ((text2.getFlags() & TEXT2_IMPLICIT_X) != 0) {
						x += glyph.getWidth();
					}
				}
			}
			break;

			default:
				glyph = cache.getFont(text2.getFont(),
						text[ptext + i] & 0x000000ff);
				if ((text2.getFlags() & TEXT2_IMPLICIT_X) == 0) {
					offset = text[ptext + (++i)] & 0x000000ff;
					if ((offset & 0x80) != 0) {
						if ((text2.getFlags() & TEXT2_VERTICAL) != 0) {
							// logger.info("y +=" + (text[ptext +
							// (i+1)]&0x000000ff) + " | " + ((text[ptext +
							// (i+2)]&0x000000ff) << 8));
							int var = this
									.twosComplement16((text[ptext + i + 1] & 0x000000ff)
											| ((text[ptext + i + 2] & 0x000000ff) << 8));
							y += var;
							i += 2;
						} else {
							int var = this
									.twosComplement16((text[ptext + i + 1] & 0x000000ff)
											| ((text[ptext + i + 2] & 0x000000ff) << 8));
							x += var;
							i += 2;
						}
					} else {
						if ((text2.getFlags() & TEXT2_VERTICAL) != 0) {
							y += offset;
						} else {
							x += offset;
						}
					}
				}
				if (glyph != null) {
					surface.drawGlyph(text2.getMixmode(), x
							+ (short) glyph.getOffset(), y
							+ (short) glyph.getBaseLine(), glyph.getWidth(),
							glyph.getHeight(), glyph.getFontData(), text2
							.getBackgroundColor(), text2
							.getForegroundColor());

					if ((text2.getFlags() & TEXT2_IMPLICIT_X) != 0) {
						x += glyph.getWidth();
					}
				}
				i++;
				break;
			}
		}
	}
}

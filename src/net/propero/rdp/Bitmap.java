/* Bitmap.java
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

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provide a class for storage of Bitmap images, along with static methods for
 * decompression and conversion of bitmaps.
 */
public class Bitmap {

	public int usage;

	private int[] highdata = null;

	private int width = 0;

	private int height = 0;

	private int x = 0;

	private int y = 0;

	private static final Logger LOGGER = LogManager.getLogger();

	static int convertTo24(Options options, int colour) {
		if (options.server_bpp == 15) {
			return convert15to24(colour);
		}
		if (options.server_bpp == 16) {
			return convert16to24(colour);
		}
		return colour;
	}

	private static int convert15to24(int colour16) {
		int r24 = (colour16 >> 7) & 0xF8;
		int g24 = (colour16 >> 2) & 0xF8;
		int b24 = (colour16 << 3) & 0xFF;

		r24 |= r24 >> 5;
		g24 |= g24 >> 5;
		b24 |= b24 >> 5;

		return (r24 << 16) | (g24 << 8) | b24;
	}

	private static int convert16to24(int colour16) {
		int r24 = (colour16 >> 8) & 0xF8;
		int g24 = (colour16 >> 3) & 0xFC;
		int b24 = (colour16 << 3) & 0xFF;

		r24 |= r24 >> 5;
		g24 |= g24 >> 6;
		b24 |= b24 >> 5;

		return (r24 << 16) | (g24 << 8) | b24;
	}

	/**
	 * Convert byte array representing a bitmap into integer array of pixels
	 *
	 * @param bitmap
	 *            Byte array of bitmap data
	 * @param Bpp
	 *            Bytes-per-pixel for bitmap
	 * @return Integer array of pixel data representing input image data
	 */
	static int[] convertImage(Options options, byte[] bitmap, int Bpp) {
		int[] out = new int[bitmap.length / Bpp];

		for (int i = 0; i < out.length; i++) {
			if (Bpp == 1) {
				out[i] = bitmap[i] & 0xFF;
			} else if (Bpp == 2) {
				out[i] = ((bitmap[i * Bpp + 1] & 0xFF) << 8)
						| (bitmap[i * Bpp] & 0xFF);
			} else if (Bpp == 3) {
				out[i] = ((bitmap[i * Bpp + 2] & 0xFF) << 16)
						| ((bitmap[i * Bpp + 1] & 0xFF) << 8)
						| (bitmap[i * Bpp] & 0xFF);
			}
			out[i] = Bitmap.convertTo24(options, out[i]);
		}
		return out;
	}

	/**
	 * Constructor for Bitmap based on integer pixel values
	 *
	 * @param data
	 *            Array of pixel data, one integer per pixel. Should have a
	 *            length of width*height.
	 * @param width
	 *            Width of bitmap represented by data
	 * @param height
	 *            Height of bitmap represented by data
	 * @param x
	 *            Desired x-coordinate of bitmap
	 * @param y
	 *            Desired y-coordinate of bitmap
	 */
	public Bitmap(int[] data, int width, int height, int x, int y) {
		this.highdata = data;
		this.width = width;
		this.height = height;
		this.x = x;
		this.y = y;
	}

	/**
	 * Constructor for Bitmap based on
	 *
	 * @param data
	 *            Array of pixel data, each pixel represented by Bpp bytes.
	 *            Should have a length of width*height*Bpp.
	 * @param width
	 *            Width of bitmap represented by data
	 * @param height
	 *            Height of bitmap represented by data
	 * @param x
	 *            Desired x-coordinate of bitmap
	 * @param y
	 *            Desired y-coordinate of bitmap
	 * @param Bpp
	 *            Number of bytes per pixel in image represented by data
	 */
	public Bitmap(Options options, byte[] data, int width, int height, int x, int y, int Bpp) {
		this.highdata = Bitmap.convertImage(options, data, Bpp);
		this.width = width;
		this.height = height;
		this.x = x;
		this.y = y;
	}

	/**
	 * Retrieve data representing this Bitmap, as an array of integer pixel
	 * values
	 *
	 * @return Bitmap pixel data
	 */
	public int[] getBitmapData() {
		return this.highdata;
	}

	/**
	 * Retrieve width of the bitmap represented by this object
	 *
	 * @return Bitmap width
	 */
	public int getWidth() {
		return this.width;
	}

	/**
	 * Retrieve height of the bitmap represented by this object
	 *
	 * @return Bitmap height
	 */
	public int getHeight() {
		return this.height;
	}

	/**
	 * Retrieve desired x-coordinate of the bitmap represented by this object
	 *
	 * @return x-coordinate of this bitmap
	 */
	public int getX() {
		return this.x;
	}

	/**
	 * Retrieve desired y-coordinate of the bitmap represented by this object
	 *
	 * @return y-coordinate of this bitmap
	 */
	public int getY() {
		return this.y;
	}

	/**
	 * Reads a color from the packet, in the given BPP.
	 *
	 * @param options Options to use (for bpp)
	 * @param Bpp <b>Bytes</b> per pixel.
	 * @param packet The packet to read from
	 * @return The color that was read
	 */
	private static int readColor(Options options, int Bpp, RdpPacket packet) {
		if (options.server_bpp == 15) {
			int lower = packet.get8();
			int upper = packet.get8();
			int full = (upper << 8) | lower;

			int r24 = (full >> 7) & 0xF8;
			r24 |= r24 >> 5;
						int g24 = (full >> 2) & 0xF8;
						g24 |= g24 >> 5;
				int b24 = (lower << 3) & 0xFF;
				b24 |= b24 >> 5;

				return (r24 << 16) | (g24 << 8) | b24;

		} else if (options.server_bpp == 16) {
			int lower = packet.get8();
			int upper = packet.get8();
			int full = (upper << 8) | lower;

			int r24 = (full >> 8) & 0xF8;
			r24 |= r24 >> 5;
				int g24 = (full >> 3) & 0xFC;
				g24 |= g24 >> 6;
		int b24 = (lower << 3) & 0xFF;
		b24 |= b24 >> 5;

		return (r24 << 16) | (g24 << 8) | b24;

		} else {
			int[] vals = new int[Bpp];
			for (int i = 0; i < vals.length; i++) {
				vals[i] = packet.get8();
			}
			int result = 0;
			for (int i = (Bpp - 1); i >= 0; i--) {
				result <<= 8;
				result |= vals[i];
			}

			return result;
		}
	}

	static final int BLACK = 0, WHITE = 0xFFFFFF;

	/**
	 * Stores the current state of decompression.
	 *
	 * @see [MS-RDPBCGR] 3.1.9 (RleDecompress; see variables at top)
	 */
	private static class DecompressionState {
		public DecompressionState(int width, int height, DecompressionCallback callback, Options options, int Bpp) {
			this.width = width;
			this.height = height;
			this.callback = callback;
			this.options = options;
			this.Bpp = Bpp;

			// Per 2.2.9.1.1.3.1.2.2: Uncompressed bitmap data is formatted as a
			// bottom-up, left-to-right series of pixels.
			// As such, x = 0, and y = height - 1;
			this.x = 0;
			this.y = height - 1;
		}

		/** Are we still on the "first" line (the bottommost one)? */
		public boolean onFirstLine = true;
		/** Do we use the foreground color? */
		public boolean insertFgColor = false;
		/** The current foreground color */
		public int fgColor = WHITE;

		/** Full width of the bitmap */
		public final int width;
		/** Full height of the bitmap */
		public final int height;
		/** The options to use when reading pixels */
		public final Options options;
		/** The number of <b>bytes</b> per pixel */
		public final int Bpp;

		/** Current x and y coordinates, will be edited as needed */
		private int x, y;

		private final DecompressionCallback callback;

		/**
		 * Writes another pixel, and moves up.
		 */
		public void writePixel(int color) throws RdesktopException {
			if (y < 0) {
				throw new RdesktopException("Can't write a pixel; y is negative!");
			}
			callback.setPixel(x, y, color);
			x++;
			if (x == width) {
				x = 0;
				y--;
			}
		}

		/**
		 * Gets the pixel in previous scanline at the same x coordinate.
		 */
		public int readBelowPixel() throws RdesktopException {
			if (y >= height - 1) {
				throw new RdesktopException(
						"Requested reading a pixel on the previous scanline, "
								+ "but there is no previous scanline!  "
								+ "(known: " + onFirstLine + "; current x=" + x
								+ ", y=" + y + ")");
			}
			return callback.getPixel(x, y + 1);
		}
	}

	/**
	 * Decompresses a bitmap into the given callback.
	 *
	 * @param options Options to use when decompressing.
	 * @param width Width of the bitmap
	 * @param height Height of the bitmap
	 * @param data Packet to read compressed data from
	 * @param size Size of the data.
	 * @param Bpp <b>bytes</b> per pixel
	 * @param callback Callback to set/get info from
	 * @throws RdesktopException
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	public static void decompress(Options options, int width, int height,
			RdpPacket data, int size, int Bpp,
			DecompressionCallback callback)
					throws RdesktopException {
		int end = data.getPosition() + size;

		DecompressionState state = new DecompressionState(width, height, callback, options, Bpp);

		while (data.getPosition() < end) {
			if (state.onFirstLine) {
				if (state.y < state.height - 1) {
					// We check this here, rather than in the middle of an order
					state.onFirstLine = false;
					state.insertFgColor = false;
				}
			}

			int code = data.get8();
			CompressionOrder order = CompressionOrder.forId(code);
			LOGGER.debug("Order: {} (for {})", order, code);
			if (order == null) {
				throw new RdesktopException("I don't know what order code " + code + " (" + Integer.toBinaryString(code) + ") means");
			}
			int runLength = order.getLength(code, data);
			LOGGER.debug("Length is {}; currently at x={}, y={}", runLength, state.x, state.y);

			if (order != CompressionOrder.REGULAR_BG_RUN
					&& order != CompressionOrder.MEGA_MEGA_BG_RUN) {
				// Subsequent BG runs need this to remain the same,
				// but other orders don't
				state.insertFgColor = false;
			}

			switch (order) {
			case REGULAR_BG_RUN:
			case MEGA_MEGA_BG_RUN: {
				handleBackgroundRun(state, runLength, data);
				break;
			}
			case REGULAR_FG_RUN:
			case MEGA_MEGA_FG_RUN:
			case LITE_SET_FG_FG_RUN:
			case MEGA_MEGA_SET_FG_RUN: {
				boolean isSet = (order == CompressionOrder.LITE_SET_FG_FG_RUN
						|| order == CompressionOrder.MEGA_MEGA_SET_FG_RUN);
				handleForegroundRun(state, runLength, data, isSet);
				break;
			}
			case LITE_DITHERED_RUN:
			case MEGA_MEGA_DITHERED_RUN: {
				handleDitheredRun(state, runLength, data);
				break;
			}
			case REGULAR_COLOR_RUN:
			case MEGA_MEGA_COLOR_RUN: {
				handleColorRun(state, runLength, data);
				break;
			}
			case REGULAR_FGBG_IMAGE:
			case MEGA_MEGA_FGBG_IMAGE:
			case LITE_SET_FG_FGBG_IMAGE:
			case MEGA_MEGA_SET_FGBG_IMAGE: {
				boolean isSet = (order == CompressionOrder.LITE_SET_FG_FGBG_IMAGE
						|| order == CompressionOrder.MEGA_MEGA_SET_FGBG_IMAGE);

				handleFgbgImage(state, runLength, data, isSet);
				break;
			}
			case REGULAR_COLOR_IMAGE:
			case MEGA_MEGA_COLOR_IMAGE: {
				handleColorImage(state, runLength, data);
				break;
			}
			case SPECIAL_FGBG_1:
			case SPECIAL_FGBG_2: {
				handleSpecialFgbg(state, data, order == CompressionOrder.SPECIAL_FGBG_2);
				break;
			}
			case WHITE:
			case BLACK: {
				handleSinglePixel(state, data, order == CompressionOrder.BLACK);
				break;
			}
			}
		}

		if (data.getPosition() != end) {
			throw new RdesktopException(
					"Read too far into compressed bitmap - expected to be at "
							+ end + " but was at " + data.getPosition());
		}
	}

	/**
	 * A Background Run Order encodes a run of pixels where each pixel in the
	 * run matches the uncompressed pixel on the previous scanline. If there is
	 * no previous scanline then each pixel in the run MUST be black.
	 * <p>
	 * When encountering back-to-back background runs, the decompressor MUST
	 * write a one-pixel foreground run to the destination buffer before
	 * processing the second background run if both runs occur on the first
	 * scanline or after the first scanline (if the first run is on the first
	 * scanline, and the second run is on the second scanline, then a one-pixel
	 * foreground run MUST NOT be written to the destination buffer). This
	 * one-pixel foreground run is counted in the length of the run.
	 * <p>
	 * The run length encodes the number of pixels in the run. There is no data
	 * associated with Background Run Orders.
	 *
	 * @param state The current state of decompression
	 * @param runLength The length of the run
	 * @param data Packet to read compressed data from
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	private static void handleBackgroundRun(DecompressionState state, int runLength, RdpPacket data) throws RdesktopException {
		if (state.onFirstLine) {
			if (state.insertFgColor) {
				state.writePixel(state.fgColor);
				runLength--;
			}
			while (runLength > 0) {
				state.writePixel(BLACK);
				runLength--;
			}
		} else {
			if (state.insertFgColor) {
				state.writePixel(state.readBelowPixel() ^ state.fgColor);
				runLength--;
			}
			while (runLength > 0) {
				state.writePixel(state.readBelowPixel());
				runLength--;
			}
		}
		// "A follow-on background run order will need a foreground pel inserted."
		// (whatever that means), as per [MS-RDPBCGR] 3.1.9
		state.insertFgColor = true;
	}

	/**
	 * A Foreground Run Order encodes a run of pixels where each pixel in the
	 * run matches the uncompressed pixel on the previous scanline XOR'd with
	 * the current foreground color. The initial foreground color MUST be white.
	 * If there is no previous scanline, then each pixel in the run MUST be set
	 * to the current foreground color.
	 * <p>
	 * The run length encodes the number of pixels in the run.
	 * <p>
	 * If the order is a "set" variant, then in addition to encoding a run of
	 * pixels, the order also encodes a new foreground color (in little-endian
	 * format) in the bytes following the optional run length. The current
	 * foreground color MUST be updated with the new value before writing the
	 * run to the destination buffer.
	 *
	 * @param state The current state of decompression
	 * @param runLength The length of the run
	 * @param data Packet to read compressed data from
	 * @param isSet True if this is a set variant
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	private static void handleForegroundRun(DecompressionState state, int runLength, RdpPacket data, boolean isSet) throws RdesktopException {
		if (isSet) {
			state.fgColor = readColor(state.options, state.Bpp, data);
		}
		if (state.onFirstLine) {
			while (runLength > 0) {
				state.writePixel(state.fgColor);
				runLength--;
			}
		} else {
			while (runLength > 0) {
				state.writePixel(state.readBelowPixel() ^ state.fgColor);
				runLength--;
			}
		}
	}

	/**
	 * A Dithered Run Order encodes a run of pixels which is composed of two
	 * alternating colors. The two colors are encoded (in little-endian format)
	 * in the bytes following the optional run length.
	 * <p>
	 * The run length encodes the number of pixel-pairs in the run (not pixels).
	 *
	 * @param state The current state of decompression
	 * @param runLength The length of the run
	 * @param data Packet to read compressed data from
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	private static void handleDitheredRun(DecompressionState state, int runLength, RdpPacket data) throws RdesktopException {
		int colorA = readColor(state.options, state.Bpp, data);
		int colorB = readColor(state.options, state.Bpp, data);

		while (runLength > 0) {
			state.writePixel(colorA);
			state.writePixel(colorB);
			runLength--;
		}
	}

	/**
	 * A Color Run Order encodes a run of pixels where each pixel is the same
	 * color. The color is encoded (in little-endian format) in the bytes
	 * following the optional run length.
	 * <p>
	 * The run length encodes the number of pixels in the run.
	 *
	 * @param state The current state of decompression
	 * @param runLength The length of the run
	 * @param data Packet to read compressed data from
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	private static void handleColorRun(DecompressionState state, int runLength, RdpPacket data) throws RdesktopException {
		int color = readColor(state.options, state.Bpp, data);

		while (runLength > 0) {
			state.writePixel(color);
			runLength--;
		}
	}

	/**
	 * A Foreground/Background Image Order encodes a binary image where each
	 * pixel in the image that is not on the first scanline fulfills exactly one
	 * of the following two properties:
	 * <ol type="a">
	 * <li>The pixel matches the uncompressed pixel on the previous scanline
	 * XOR'ed with the current foreground color.</li>
	 * <li>The pixel matches the uncompressed pixel on the previous scanline.</li>
	 * </ol>
	 * <p>
	 * If the pixel is on the first scanline then it fulfills exactly one of the
	 * following two properties:
	 * <ol type="a" start="3">
	 * <li>The pixel is the current foreground color.</li>
	 * <li>The pixel is black.</li>
	 * </ol>
	 * <p>
	 * The binary image is encoded as a sequence of byte-sized bitmasks which
	 * follow the optional run length (the last bitmask in the sequence can be
	 * smaller than one byte in size). If the order is a "set" variant then the
	 * bitmasks MUST follow the bytes which specify the new foreground color.
	 * Each bit in the encoded bitmask sequence represents one pixel in the
	 * image. A bit that has a value of 1 represents a pixel that fulfills
	 * either property (a) or (c), while a bit that has a value of 0 represents
	 * a pixel that fulfills either property (b) or (d). The individual bitmasks
	 * MUST each be processed from the low-order bit to the high-order bit.
	 * <p>
	 * The run length encodes the number of pixels in the run.
	 * <p>
	 * If the order is a "set" variant, then in addition to encoding a binary
	 * image, the order also encodes a new foreground color (in little-endian
	 * format) in the bytes following the optional run length. The current
	 * foreground color MUST be updated with the new value before writing the
	 * run to the destination buffer.
	 *
	 * @param state The current state of decompression
	 * @param runLength The length of the run
	 * @param data Packet to read compressed data from
	 * @param isSet True if this is a set variant
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	private static void handleFgbgImage(DecompressionState state, int runLength, RdpPacket data, boolean isSet) throws RdesktopException {
		if (isSet) {
			state.fgColor = readColor(state.options, state.Bpp, data);
		}

		while (runLength > 8) {
			int bitmask = data.get8();
			if (state.onFirstLine) {
				writeFirstLineFgbg(state, bitmask, 8);
			} else {
				writeNormalFgbg(state, bitmask, 8);
			}
			runLength -= 8;
		}
		// Remaining bits
		if (runLength > 0) {
			int bitmask = data.get8();
			if (state.onFirstLine) {
				writeFirstLineFgbg(state, bitmask, runLength);
			} else {
				writeNormalFgbg(state, bitmask, runLength);
			}
		}
	}

	/**
	 * A Color Image Order encodes a run of uncompressed pixels.
	 * <p>The run length encodes the number of pixels in the run. So, to compute the actual number of bytes which follow the optional run length, the run length MUST be multiplied by the color depth (in bits-per-pixel) of the bitmap data.
	 *
	 * @param state The current state of decompression
	 * @param runLength The length of the run
	 * @param data Packet to read compressed data from
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	private static void handleColorImage(DecompressionState state, int runLength, RdpPacket data) throws RdesktopException {
		while (runLength > 0) {
			state.writePixel(readColor(state.options, state.Bpp, data));
			runLength--;
		}
	}

	/**
	 * Masks used by the special FGBG packets.
	 */
	private static final int SPECIAL_FGBG_MASK_1 = 0x03, SPECIAL_FGBG_MASK_2 = 0x05;
	/**
	 * The compression order encodes a foreground/background image with an 8-bit
	 * bitmask of (0x03 or 0x05).
	 *
	 * @param state The current state of decompression
	 * @param data Packet to read compressed data from
	 * @param type2 If true, bitmask is {@value #SPECIAL_FGBG_MASK_2}. If false, bitmask is {@value #SPECIAL_FGBG_MASK_1}.
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	private static void handleSpecialFgbg(DecompressionState state, RdpPacket data, boolean type2) throws RdesktopException {
		if (state.onFirstLine) {
			writeFirstLineFgbg(state, type2 ? SPECIAL_FGBG_MASK_2 : SPECIAL_FGBG_MASK_1, 8);
		} else {
			writeNormalFgbg(state, type2 ? SPECIAL_FGBG_MASK_2 : SPECIAL_FGBG_MASK_1, 8);
		}
	}

	/**
	 * The compression order encodes a single (white or black) pixel.
	 *
	 * @param state The current state of decompression
	 * @param data Packet to read compressed data from
	 * @param black True if the pixel is black; false if white
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	private static void handleSinglePixel(DecompressionState state, RdpPacket data, boolean black) throws RdesktopException {
		state.writePixel(black ? BLACK : WHITE);
	}

	/** Helper for FGBG images */
	private static void writeNormalFgbg(DecompressionState state, int bitmask, int numBits) throws RdesktopException {
		for (int i = 0; i < numBits; i++) {
			boolean mode = (bitmask & 1) == 1;
			if (mode) {
				state.writePixel(state.readBelowPixel() ^ state.fgColor);
			} else {
				state.writePixel(state.readBelowPixel());
			}
			bitmask >>= 1;
		}
	}

	/** Helper for FGBG images */
	private static void writeFirstLineFgbg(DecompressionState state, int bitmask, int numBits) throws RdesktopException {
		for (int i = 0; i < numBits; i++) {
			boolean mode = (bitmask & 1) == 1;
			if (mode) {
				state.writePixel(state.fgColor);
			} else {
				state.writePixel(BLACK);
			}
			bitmask >>= 1;
		}
	}

	/**
	 * Decompress bitmap data from packet and output directly to supplied image
	 * object
	 *
	 * @param width
	 *            Width of bitmap to decompress
	 * @param height
	 *            Height of bitmap to decompress
	 * @param size
	 *            Size of compressed data in bytes
	 * @param data
	 *            Packet containing bitmap data
	 * @param Bpp
	 *            Bytes per-pixel for bitmap
	 * @param cm
	 *            Colour model of bitmap
	 * @param left
	 *            X offset for drawing bitmap
	 * @param top
	 *            Y offset for drawing bitmap
	 * @param w
	 *            Image to draw bitmap to
	 * @return Original image object, with decompressed bitmap drawn at
	 *         specified coordinates
	 * @throws RdesktopException
	 */
	public static WrappedImage decompressImgDirect(Options options, int width, int height,
			int size, RdpPacket data, int Bpp, IndexColorModel cm,
			int left, int top, WrappedImage w) throws RdesktopException {

		decompress(options, width, height, data, size, Bpp,
				new DecompressionCallback() {
			@Override
			public void setPixel(int x, int y, int color) {
				w.setRGB(left + x, top + y, color);
			}

			@Override
			public int getPixel(int x, int y) {
				return w.getRGB(left + x, top + y);
			}
		});

		return w;
	}

	/**
	 * Decompress bitmap data from packet and output as an Image
	 *
	 * @param width
	 *            Width of bitmap
	 * @param height
	 *            Height of bitmap
	 * @param size
	 *            Size of compressed data in bytes
	 * @param data
	 *            Packet containing bitmap data
	 * @param Bpp
	 *            Bytes per-pixel for bitmap
	 * @param cm
	 *            Colour model for bitmap (if using indexed palette)
	 * @return Decompressed bitmap as Image object
	 * @throws RdesktopException
	 */
	public static Image decompressImg(Options options, int width, int height, int size,
			RdpPacket data, int Bpp, IndexColorModel cm)
					throws RdesktopException {

		WrappedImage w;

		if (cm == null) {
			w = new WrappedImage(width, height, BufferedImage.TYPE_INT_RGB);
		} else {
			w = new WrappedImage(width, height, BufferedImage.TYPE_INT_RGB, cm);
		}

		decompress(options, width, height, data, size, Bpp,
				new DecompressionCallback() {
			@Override
			public void setPixel(int x, int y, int color) {
				w.setRGB(x, y, color);
			}

			@Override
			public int getPixel(int x, int y) {
				return w.getRGB(x, y);
			}
		});

		return w.getBufferedImage();
	}

	/**
	 * Decompress bitmap data from packet and store in array of integers
	 *
	 * @param width
	 *            Width of bitmap
	 * @param height
	 *            Height of bitmap
	 * @param size
	 *            Size of compressed data in bytes
	 * @param data
	 *            Packet containing bitmap data
	 * @param Bpp
	 *            Bytes per-pixel for bitmap
	 * @return Integer array of pixels containing decompressed bitmap data
	 * @throws RdesktopException
	 */
	public static int[] decompressInt(Options options, int width, int height, int size,
			RdpPacket data, int Bpp) throws RdesktopException {

		int[] pixel = new int[width * height];

		decompress(options, width, height, data, size, Bpp,
				new DecompressionCallback() {
			@Override
			public void setPixel(int x, int y, int color) {
				pixel[(y * width) + x] = color;
			}

			@Override
			public int getPixel(int x, int y) {
				return pixel[(y * width) + x];
			}
		});

		return pixel;
	}

	/**
	 * Decompress bitmap data from packet and store in array of bytes
	 *
	 * @param width
	 *            Width of bitmap
	 * @param height
	 *            Height of bitmap
	 * @param size
	 *            Size of compressed data in bytes
	 * @param data
	 *            Packet containing bitmap data
	 * @param Bpp
	 *            Bytes per-pixel for bitmap
	 * @return Byte array of pixels containing decompressed bitmap data
	 * @throws RdesktopException
	 */
	public static byte[] decompress(Options options, int width, int height, int size,
			RdpPacket data, int Bpp) throws RdesktopException {

		byte[] pixel = new byte[width * height];

		decompress(options, width, height, data, size, Bpp,
				new DecompressionCallback() {
			@Override
			public void setPixel(int x, int y, int color) {
				pixel[(y * width) + x] = (byte) color;
			}

			@Override
			public int getPixel(int x, int y) {
				return pixel[(y * width) + x];
			}
		});

		return pixel;
	}

	/**
	 * Callback to be used with the decompression methods to add to images.
	 */
	public static interface DecompressionCallback {
		/**
		 * Gets the value for the given pixel in the original image.
		 *
		 * @param x X coordinate in the decompressed bitmap; should be translated
		 * @param y Y coordinate in the decompressed bitmap; should be translated
		 * @return color Raw color of the pixel
		 */
		public abstract int getPixel(int x, int y);

		/**
		 * Called for the given pixel in the decompressed image.
		 *
		 * @param x X coordinate in the decompressed bitmap
		 * @param y Y coordinate in the decompressed bitmap
		 * @param color Raw color of the pixel
		 */
		public abstract void setPixel(int x, int y, int color);
	}

	/**
	 * Different compression orders within an RLE Compressed Bitmap Stream.
	 *
	 * The way these are differentiated is <i>extermely</i> confusing, because
	 * the ID can also contain the length, but only sometimes.
	 *
	 * @see [MS-RDPBCGR] 2.2.9.1.1.3.1.2.4
	 */
	static enum CompressionOrder {
		REGULAR_BG_RUN          (0b000_00000, Type.REGULAR),
		MEGA_MEGA_BG_RUN        (0b1111_0000, Type.MEGA_MEGA),
		REGULAR_FG_RUN          (0b001_00000, Type.REGULAR),
		MEGA_MEGA_FG_RUN        (0b1111_0001, Type.MEGA_MEGA),
		LITE_SET_FG_FG_RUN      (0b1100_0000, Type.LITE),
		MEGA_MEGA_SET_FG_RUN    (0b1111_0110, Type.MEGA_MEGA),
		LITE_DITHERED_RUN       (0b1110_0000, Type.LITE),
		MEGA_MEGA_DITHERED_RUN  (0b1111_1000, Type.MEGA_MEGA),
		REGULAR_COLOR_RUN       (0b011_00000, Type.REGULAR),
		MEGA_MEGA_COLOR_RUN     (0b1111_0011, Type.MEGA_MEGA),
		REGULAR_FGBG_IMAGE      (0b010_00000, Type.REG_FGBG),
		MEGA_MEGA_FGBG_IMAGE    (0b1111_0010, Type.MEGA_MEGA),
		LITE_SET_FG_FGBG_IMAGE  (0b1101_0000, Type.LITE_FGBG),
		MEGA_MEGA_SET_FGBG_IMAGE(0b1111_0111, Type.MEGA_MEGA),
		REGULAR_COLOR_IMAGE     (0b100_00000, Type.REGULAR),
		MEGA_MEGA_COLOR_IMAGE   (0b1111_0100, Type.MEGA_MEGA),
		SPECIAL_FGBG_1          (0b1111_1001, Type.SINGLE_BYTE),
		SPECIAL_FGBG_2          (0b1111_1010, Type.SINGLE_BYTE),
		WHITE                   (0b1111_1101, Type.SINGLE_BYTE),
		BLACK                   (0b1111_1110, Type.SINGLE_BYTE);

		private final int id;
		private final Type type;

		private CompressionOrder(int id, Type type) {
			this.id = id;
			this.type = type;
		}

		/**
		 * Attempts to parse the given order header ID into a CompressionOrder.
		 *
		 * May return null.
		 *
		 * @param id The compression order header byte
		 * @return The value
		 */
		public static CompressionOrder forId(int id) {
			for (CompressionOrder order : values()) {
				if (order.matches(id)) {
					return order;
				}
			}
			return null;
		}

		/**
		 * Checks if the given ID matches this order's ID.
		 * @param id The ID to check
		 * @return True if it matches
		 */
		public boolean matches(int id) {
			int effId = id & this.type.idMask;
			return (effId == this.id);
		}

		/**
		 * Returns the ID associated with this order, which may be one of
		 * several accepted values depending on the type.
		 *
		 * @return {@link #id}
		 */
		public int getId() {
			return id;
		}

		/**
		 * Returns the type of this order.
		 *
		 * @return {@link #type}
		 */
		public Type getType() {
			return type;
		}

		/**
		 * Gets the length of the compression order.
		 *
		 * @param start The first byte read, including the ID
		 * @param data The rest
		 * @return Number of bytes to read
		 */
		public int getLength(int start, RdpPacket data) {
			return type.getLength(start, data);
		}

		public static enum Type {
			REGULAR(0b11100000) {
				@Override
				public int getLength(int start, RdpPacket data) {
					int len = start & REG_MASK;
					if (len != 0) {
						LOGGER.trace("Regular - len={}", len);
						return len;
					} else {
						// MEGA run; 1 extra byte
						int num = data.get8();
						int val = num + REG_COUNT;
						LOGGER.trace("Regular MEGA - read={} => {}", num, val);
						return val;
					}
				}
			},
			LITE(0b11110000) {
				@Override
				public int getLength(int start, RdpPacket data) {
					int len = start & LITE_MASK;
					if (len != 0) {
						LOGGER.trace("Lite - len={}", len);
						return len;
					} else {
						// MEGA run; 1 extra byte
						int num = data.get8();
						int val = num + LITE_COUNT;
						LOGGER.trace("Lite MEGA - read={} => {}", num, val);
						return val;
					}
				}
			},
			MEGA_MEGA(0b11111111) {
				@Override
				public int getLength(int start, RdpPacket data) {
					int value = data.getLittleEndian16();
					LOGGER.trace("MEGA MEGA - read={}", value);
					return value;
				}
			},
			SINGLE_BYTE(0b11111111) {
				@Override
				public int getLength(int start, RdpPacket data) {
					LOGGER.trace("Single byte");
					return 0;
				}
			},
			REG_FGBG(0b11100000) {
				@Override
				public int getLength(int start, RdpPacket data) {
					int len = start & REG_MASK;
					if (len != 0) {
						int val = len * 8;
						LOGGER.trace("Regular FGBG - len={} => {}", len, val);
						return val;
					} else {
						int read = data.get8();
						int val = read + 1; // Yes, + 1, not another number
						LOGGER.trace("\"MEGA\" Regular FGBG - read={} => {}", read, val);
						return val;
					}
				}
			},
			LITE_FGBG(0b11110000) {
				@Override
				public int getLength(int start, RdpPacket data) {
					int len = start & LITE_MASK;
					if (len != 0) {
						int val = len * 8;
						LOGGER.trace("Lite FGBG - len={} => {}", len, val);
						return val;
					} else {
						int read = data.get8();
						int val = read + 1; // Again, + 1
						LOGGER.trace("\"MEGA\" Lite FGBG - read={} => {}", read, val);
						return val;
					}
				}
			};

			private static final int REG_MASK  = 0b00011111, REG_COUNT  = REG_MASK + 1;
			private static final int LITE_MASK = 0b00001111, LITE_COUNT = LITE_MASK + 1;

			public final int idMask;

			private Type(int idMask) {
				this.idMask = idMask;
			}

			public abstract int getLength(int start, RdpPacket data);
		}
	}
}

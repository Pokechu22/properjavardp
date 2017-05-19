package net.propero.rdp;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import java.util.EnumSet;
import java.util.Set;

import net.propero.rdp.Bitmap.CompressionOrder;
import net.propero.rdp.Bitmap.CompressionOrder.Type;
import net.propero.rdp.Bitmap.DecompressionCallback;

import org.junit.Test;

public class BitmapTest {

	/**
	 * Verifies that each possible id maps no more than one compression order.
	 */
	@Test
	public void testUniqueCompressionOrders() {
		Set<CompressionOrder> matching = EnumSet.noneOf(CompressionOrder.class);
		for (int i = 0; i < 255; i++) {
			matching.clear();

			for (CompressionOrder order : CompressionOrder.values()) {
				if (order.matches(i)) {
					matching.add(order);
				}
			}

			assertThat("There should only be one order that matches " + i + " ("
					+ Integer.toBinaryString(i) + ")", matching, either(empty()).or(hasSize(1)));
		}
	}

	/**
	 * Verifies that the IDs given for each compression order map to that order.
	 */
	@Test
	public void testCompressionOrderIds() {
		for (CompressionOrder order : CompressionOrder.values()) {
			int id = order.getId();
			assertThat("Broken mapping for " + id + " (" + Integer.toBinaryString(id)
					+ ")", CompressionOrder.forId(id), is(order));
			assertTrue(order.matches(order.getId()));
		}
	}

	@Test
	public void testLengthRegular() {
		for (int i = 0b00001; i <= 0b11111; i++) {
			assertThat(Type.REGULAR.getLength(i, packet()), is(i));
		}
		// MEGA variant
		assertThat(Type.REGULAR.getLength(0, packet(0)), is(32));
		assertThat(Type.REGULAR.getLength(0, packet(1)), is(33));
		assertThat(Type.REGULAR.getLength(0, packet(16)), is(48));
	}

	@Test
	public void testLengthLite() {
		for (int i = 0b0001; i <= 0b1111; i++) {
			assertThat(Type.LITE.getLength(i, packet()), is(i));
		}
		// MEGA variant
		assertThat(Type.LITE.getLength(0, packet(0)), is(16));
		assertThat(Type.LITE.getLength(0, packet(1)), is(17));
		assertThat(Type.LITE.getLength(0, packet(16)), is(32));
	}

	@Test
	public void testLengthMegaMega() {
		assertThat(Type.MEGA_MEGA.getLength(0, packet(0, 0)), is(0));
		assertThat(Type.MEGA_MEGA.getLength(0, packet(1, 0)), is(1));
		assertThat(Type.MEGA_MEGA.getLength(0, packet(127, 0)), is(127));
		assertThat(Type.MEGA_MEGA.getLength(0, packet(128, 0)), is(128));
		assertThat(Type.MEGA_MEGA.getLength(0, packet(255, 0)), is(255));
		assertThat(Type.MEGA_MEGA.getLength(0, packet(0, 1)), is(256));
		assertThat(Type.MEGA_MEGA.getLength(0, packet(1, 1)), is(257));
	}

	@Test
	public void testLengthSingleByte() {
		assertThat(Type.SINGLE_BYTE.getLength(0, packet()), is(0));
		assertThat(Type.SINGLE_BYTE.getLength(1, packet()), is(0));
		assertThat(Type.SINGLE_BYTE.getLength(255, packet()), is(0));
	}

	@Test
	public void testLengthRegFgbg() {
		for (int i = 0b00001; i <= 0b11111; i++) {
			assertThat(Type.REG_FGBG.getLength(i, packet()), is(i * 8));
		}
		// MEGA variant
		assertThat(Type.REG_FGBG.getLength(0, packet(0)), is(1));
		assertThat(Type.REG_FGBG.getLength(0, packet(1)), is(2));
		assertThat(Type.REG_FGBG.getLength(0, packet(16)), is(17));
	}

	@Test
	public void testLengthLiteFgbg() {
		for (int i = 0b0001; i <= 0b1111; i++) {
			assertThat(Type.LITE_FGBG.getLength(i, packet()), is(i * 8));
		}
		// MEGA variant
		assertThat(Type.LITE_FGBG.getLength(0, packet(0)), is(1));
		assertThat(Type.LITE_FGBG.getLength(0, packet(1)), is(2));
		assertThat(Type.LITE_FGBG.getLength(0, packet(16)), is(17));
	}

	@Test
	public void testColorRun() throws RdesktopException {
		byte[] data = {
			(byte) 0b011_00100, // REGULAR_COLOR_RUN, 4 items
			(byte) 'A', // A color

			(byte) 0b011_00010, // REGULAR_COLOR_RUN, 2 pixels
			(byte) 'B', // A second color

			(byte) 0b011_00110, // REGULAR_COLOR_RUN, 6 pixels (wraps around!)
			(byte) 'C', // A third color

			(byte) 0b011_00001, // REGULAR_COLOR_RUN, 1 pixel
			(byte) 'D', // A forth color

			(byte) 0b011_00010, // REGULAR_COLOR_RUN, 2 pixels
			(byte) 'E', // A fifth color

			(byte) 0b011_00001, // REGULAR_COLOR_RUN, 1 pixel
			(byte) 'F', // A sixth color
		};

		int[][] image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 'A', 'A', 'A', 'A' }));
		assertThat("Row 2", image[2], is(new int[] { 'B', 'B', 'C', 'C' }));
		assertThat("Row 1", image[1], is(new int[] { 'C', 'C', 'C', 'C' }));
		assertThat("Row 0", image[0], is(new int[] { 'D', 'E', 'E', 'F' }));
	}

	@Test
	public void testColorImage() throws RdesktopException {
		byte[] data = {
			(byte) 0b100_10000, // REGULAR_COLOR_IMAGE, length 16
			'A', 'A', 'B', 'B',
			'A', 'A', 'B', 'C',
			'C', 'A', 'C', 'C',
			'D', 'C', 'C', 'C'
		};

		int[][] image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 'A', 'A', 'B', 'B' }));
		assertThat("Row 2", image[2], is(new int[] { 'A', 'A', 'B', 'C' }));
		assertThat("Row 1", image[1], is(new int[] { 'C', 'A', 'C', 'C' }));
		assertThat("Row 0", image[0], is(new int[] { 'D', 'C', 'C', 'C' }));
	}

	@Test
	public void testDitheredRun() throws RdesktopException {
		byte[] data = {
			(byte) 0b1110_0100, // LITE_DITHERED_RUN, 4 pairs = 8 pixels
			'A', 'B', // Colors
			(byte) 0b1110_0001, // LITE_DITHERED_RUN, 1 pair = 2 pixels
			'C' ,'D',
			(byte) 0b1110_0011, // LITE_DITHERED_RUN, 3 pairs = 6 pixels
			'E', 'F'
		};

		int[][] image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 'A', 'B', 'A', 'B' }));
		assertThat("Row 2", image[2], is(new int[] { 'A', 'B', 'A', 'B' }));
		assertThat("Row 1", image[1], is(new int[] { 'C', 'D', 'E', 'F' }));
		assertThat("Row 0", image[0], is(new int[] { 'E', 'F', 'E', 'F' }));
	}

	@Test
	public void testBgRun() throws RdesktopException {
		// Simple test
		byte[] data = {
			(byte) 0b000_10000, // REGULAR_BG_RUN, 16 pixels
		};

		int[][] image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 0, 0, 0, 0 }));
		assertThat("Row 2", image[2], is(new int[] { 0, 0, 0, 0 }));
		assertThat("Row 1", image[1], is(new int[] { 0, 0, 0, 0 }));
		assertThat("Row 0", image[0], is(new int[] { 0, 0, 0, 0 }));

		// Copying
		data = new byte[] {
			(byte) 0b100_00100, // REGULAR_COLOR_IMAGE, length 4
			'A', 'B', 'C', 'D',
			(byte) 0b000_01100, // REGULAR_BG_RUN, 12 pixels
		};

		image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 'A', 'B', 'C', 'D' }));
		assertThat("Row 2", image[2], is(new int[] { 'A', 'B', 'C', 'D' }));
		assertThat("Row 1", image[1], is(new int[] { 'A', 'B', 'C', 'D' }));
		assertThat("Row 0", image[0], is(new int[] { 'A', 'B', 'C', 'D' }));
	}

	@Test
	public void testMultilineBgRun() throws RdesktopException {
		// Per the section on Background Run Orders ([MS-RDPBCGR] 2.2.9.1.1.3.1.2.4):

		// When encountering back-to-back background runs, the decompressor MUST
		// write a one-pixel foreground run to the destination buffer before
		// processing the second background run if both runs occur on the first
		// scanline or after the first scanline (if the first run is on the
		// first scanline, and the second run is on the second scanline, then a
		// one-pixel foreground run MUST NOT be written to the destination
		// buffer). This one-pixel foreground run is counted in the length of
		// the run.

		// So: This does _NOT_ trigger it (one on the first line, and the rest on the next):
		byte[] data = {
			(byte) 0b000_00100, // REGULAR_BG_RUN, 4 pixels
			(byte) 0b000_01100, // REGULAR_BG_RUN, 12 pixels
		};

		int[][] image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 0, 0, 0, 0 }));
		assertThat("Row 2", image[2], is(new int[] { 0, 0, 0, 0 }));
		assertThat("Row 1", image[1], is(new int[] { 0, 0, 0, 0 }));
		assertThat("Row 0", image[0], is(new int[] { 0, 0, 0, 0 }));

		// This does, on the first row:
		data = new byte[] {
			(byte) 0b000_00010, // REGULAR_BG_RUN, 2 pixels
			(byte) 0b000_00010, // REGULAR_BG_RUN, 2 pixels
			(byte) 0b000_01100, // REGULAR_BG_RUN, 12 pixels
		};

		image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 0, 0, Bitmap.WHITE, 0 }));
		assertThat("Row 2", image[2], is(new int[] { 0, 0, Bitmap.WHITE, 0 })); // Copied
		assertThat("Row 1", image[1], is(new int[] { 0, 0, Bitmap.WHITE, 0 }));
		assertThat("Row 0", image[0], is(new int[] { 0, 0, Bitmap.WHITE, 0 }));

		// This also doesn't trigger it (on first line -> not on first line)
		data = new byte[] {
			(byte) 0b000_01000, // REGULAR_BG_RUN, 8 pixels
			(byte) 0b000_01000, // REGULAR_BG_RUN, 8 pixels
		};

		image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 0, 0, 0, 0 }));
		assertThat("Row 2", image[2], is(new int[] { 0, 0, 0, 0 }));
		assertThat("Row 1", image[1], is(new int[] { 0, 0, 0, 0 }));
		assertThat("Row 0", image[0], is(new int[] { 0, 0, 0, 0 }));

		// This also does (length of 1):
		data = new byte[] {
			(byte) 0b000_00010, // REGULAR_BG_RUN, 2 pixels
			(byte) 0b000_00001, // REGULAR_BG_RUN, 1 pixel
			(byte) 0b000_00001, // REGULAR_BG_RUN, 1 pixel
			(byte) 0b000_01100, // REGULAR_BG_RUN, 12 pixels
		};

		image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 0, 0, Bitmap.WHITE, Bitmap.WHITE }));
		assertThat("Row 2", image[2], is(new int[] { 0, 0, Bitmap.WHITE, Bitmap.WHITE })); // Copied
		assertThat("Row 1", image[1], is(new int[] { 0, 0, Bitmap.WHITE, Bitmap.WHITE }));
		assertThat("Row 0", image[0], is(new int[] { 0, 0, Bitmap.WHITE, Bitmap.WHITE }));
	}

	@Test
	public void testFgbgImage() throws RdesktopException {
		byte[] data = {
			(byte) 0b1101_0000, // LITE_SET_FG_FGBG_IMAGE
			3, // 4 items (note: incremented)
			(byte) 0b00001111, // One color
			(byte) 0b0000_0101, // Mask (from first to last): fore black fore black

			(byte) 0b1101_0000, // LITE_SET_FG_FGBG_IMAGE
			3, // 4 items (note: incremented)
			(byte) 0b00111100, // Another color
			(byte) 0b0000_0011, // Mask: forexor forexor prev prev

			(byte) 0b1101_0000, // LITE_SET_FG_FGBG_IMAGE, 4 items
			3, // 4 items (note: incremented)
			(byte) 0b10101010, // A third color
			(byte) 0b0000_0000, // Mask: prev prev prev prev

			(byte) 0b1101_0000, // LITE_SET_FG_FGBG_IMAGE, 4 items
			3, // 4 items (note: incremented)
			(byte) 0b11111111, // A last color
			(byte) 0b0000_1111, // Mask: forexor forexor forexor forexor
		};

		int[][] image = decompress(data);

		assertThat("Row 3", image[3], is(new int[] { 0b00001111, 0b00000000, 0b00001111, 0b00000000 }));
		assertThat("Row 2", image[2], is(new int[] { 0b00110011, 0b00111100, 0b00001111, 0b00000000 }));
		assertThat("Row 1", image[1], is(new int[] { 0b00110011, 0b00111100, 0b00001111, 0b00000000 }));
		assertThat("Row 0", image[0], is(new int[] { 0b11001100, 0b11000011, 0b11110000, 0b11111111 }));
	}

	/**
	 * Helper that decompresses a 4x4, 1 byte per pixel bitmap
	 *
	 * @param data Bytes to decompress
	 * @return The decompressed image ([y][x])
	 * @throws RdesktopException
	 */
	private static int[][] decompress(byte[] data) throws RdesktopException {
		Options options = new Options();
		options.server_bpp = 8;
		options.Bpp = 1;

		int[][] result = new int[4][4];

		RdpPacket_Localised packet = packet(data);

		Bitmap.decompress(options, 4, 4, packet, data.length, options.Bpp, new DecompressionCallback() {
			@Override
			public void setPixel(int x, int y, int color) {
				result[y][x] = color;
			}

			@Override
			public int getPixel(int x, int y) {
				return result[y][x];
			}
		});

		return result;
	}

	/**
	 * Returns an empty, unreadable packet.
	 */
	private static RdpPacket_Localised packet() {
		return RdpPacket_Localised.EMPTY;
	}
	/**
	 * Creates a packet containing the given bytes
	 */
	private static RdpPacket_Localised packet(int first, int... rest) {
		RdpPacket_Localised packet = new RdpPacket_Localised(1 + rest.length);
		packet.set8(first);
		for (int val : rest) {
			packet.set8(val);
		}
		packet.setPosition(0);
		return packet;
	}
	/**
	 * Creates a packet containing the given (unsigned) bytes
	 */
	private static RdpPacket_Localised packet(byte[] data) {
		RdpPacket_Localised packet = new RdpPacket_Localised(data.length);
		for (byte val : data) {
			packet.set8(val & 0xFF);
		}
		packet.setPosition(0);
		return packet;
	}
}

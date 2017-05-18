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
	public void testFgbg() throws Exception {
		Options options = new Options();
		options.server_bpp = 8;
		options.Bpp = 1;
		int[][] data = new int[4][4];

		byte[] compressedData = {
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
		RdpPacket_Localised packet = new RdpPacket_Localised(compressedData.length);
		for (byte b : compressedData) {
			packet.set8(b & 0xFF);
		}
		packet.setPosition(0);

		Bitmap.decompress(options, 4, 4, packet, compressedData.length, options.Bpp, new DecompressionCallback() {
			@Override
			public void setPixel(int x, int y, int color) {
				data[y][x] = color;
			}

			@Override
			public int getPixel(int x, int y) {
				return data[y][x];
			}
		});

		assertThat("Row 3", data[3], is(new int[] { 0b00001111, 0b00000000, 0b00001111, 0b00000000 }));
		assertThat("Row 2", data[2], is(new int[] { 0b00110011, 0b00111100, 0b00001111, 0b00000000 }));
		assertThat("Row 1", data[1], is(new int[] { 0b00110011, 0b00111100, 0b00001111, 0b00000000 }));
		assertThat("Row 0", data[0], is(new int[] { 0b11001100, 0b11000011, 0b11110000, 0b11111111 }));
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
}

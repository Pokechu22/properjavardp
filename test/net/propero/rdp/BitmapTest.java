package net.propero.rdp;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import java.util.EnumSet;
import java.util.Set;

import net.propero.rdp.Bitmap.CompressionOrder;
import net.propero.rdp.Bitmap.CompressionOrder.Type;

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

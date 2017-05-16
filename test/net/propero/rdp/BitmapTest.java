package net.propero.rdp;

import static org.junit.Assert.*;

import org.junit.Test;

public class BitmapTest {

	@Test
	public void testUniqueCompressionOrders() {
		for (int i = 0; i < 255; i++) {
			Bitmap.CompressionOrder.forId(i); // Will assert if there's a duplicate
		}
	}

}

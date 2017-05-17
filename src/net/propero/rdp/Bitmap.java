/* Bitmap.java
 * Component: ProperJavaRDP
 * 
 * Revision: $Revision$
 * Author: $Author$
 * Date: $Date$
 *
 * Copyright (c) 2005 Propero Limited
 *
 * Purpose: Provide a class for storage of Bitmap images, along with
 *          static methods for decompression and conversion of bitmaps.
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

public class Bitmap {

	public int usage;

	private int[] highdata = null;

	private int width = 0;

	private int height = 0;

	private int x = 0;

	private int y = 0;

	protected static Logger logger = LogManager.getLogger(Rdp.class);

	static int convertTo24(Options options, int colour) {
		if (options.server_bpp == 15)
			return convert15to24(colour);
		if (options.server_bpp == 16)
			return convert16to24(colour);
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
	 * Read integer of a specified byte-length from byte array
	 * 
	 * @param data
	 *            Array to read from
	 * @param offset
	 *            Offset in array to read from
	 * @param Bpp
	 *            Number of bytes to read
	 * @return
	 */
	private static int cvalx(Options options, byte[] data, int offset, int Bpp) {
		int rv = 0;
		if (options.server_bpp == 15) {
			int lower = data[offset] & 0xFF;
			int full = (data[offset + 1] & 0xFF) << 8 | lower;

			int r24 = (full >> 7) & 0xF8;
			r24 |= r24 >> 5;
			int g24 = (full >> 2) & 0xF8;
			g24 |= g24 >> 5;
			int b24 = (lower << 3) & 0xFF;
			b24 |= b24 >> 5;

			return (r24 << 16) | (g24 << 8) | b24;

		} else if (options.server_bpp == 16) {
			int lower = data[offset] & 0xFF;
			int full = (data[offset + 1] & 0xFF) << 8 | lower;

			int r24 = (full >> 8) & 0xF8;
			r24 |= r24 >> 5;
			int g24 = (full >> 3) & 0xFC;
			g24 |= g24 >> 6;
			int b24 = (lower << 3) & 0xFF;
			b24 |= b24 >> 5;

			return (r24 << 16) | (g24 << 8) | b24;

		} else {
			for (int i = (Bpp - 1); i >= 0; i--) {
				rv = rv << 8;
				rv |= data[offset + i] & 0xFF;
			}
		}

		return rv;
	}

	/**
	 * 
	 * @param input
	 * @param startOffset
	 * @param offset
	 * @param Bpp
	 * @return
	 */
	private static int getli(byte[] input, int startOffset, int offset, int Bpp) {
		int rv = 0;

		int rOffset = startOffset + (offset * Bpp);
		for (int i = 0; i < Bpp; i++) {
			rv = rv << 8;
			rv |= (input[rOffset + (Bpp - i - 1)]) & 0xFF;
		}
		return rv;
	}

	/**
	 * 
	 * @param input
	 * @param startlocation
	 * @param offset
	 * @param value
	 * @param Bpp
	 */
	private static void setli(byte[] input, int startlocation, int offset, int value,
			int Bpp) {
		int location = startlocation + offset * Bpp;

		input[location] = (byte) (value & 0xFF);
		if (Bpp > 1)
			input[location + 1] = (byte) ((value & 0xFF00) >> 8);
		if (Bpp > 2)
			input[location + 2] = (byte) ((value & 0xFF0000) >> 16);
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
			if (Bpp == 1)
				out[i] = bitmap[i] & 0xFF;
			else if (Bpp == 2)
				out[i] = ((bitmap[i * Bpp + 1] & 0xFF) << 8)
						| (bitmap[i * Bpp] & 0xFF);
			else if (Bpp == 3)
				out[i] = ((bitmap[i * Bpp + 2] & 0xFF) << 16)
						| ((bitmap[i * Bpp + 1] & 0xFF) << 8)
						| (bitmap[i * Bpp] & 0xFF);
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
	 * Decompresses a bitmap into the given callback.
	 *
	 * @param options Options to use when decompressing.
	 * @param width Width of the bitmap
	 * @param height Height of the bitmap
	 * @param compressedData Existing compressed data
	 * @param Bpp <b>bytes</b> per pixel
	 * @param callback Callback to set/get info from
	 * @throws RdesktopException
	 */
	public static void decompress(Options options, int width, int height,
			byte[] compressedData, int Bpp, DecompressionCallback callback)
					throws RdesktopException {
		int previous = -1, line = 0, prevY = 0;
		int input = 0, end = compressedData.length;
		int opcode = 0, count = 0, offset = 0, x = width;
		int lastopcode = -1, fom_mask = 0;
		int code = 0, color1 = 0, color2 = 0;
		byte mixmask = 0;
		int mask = 0;
		int mix = 0xffffffff;

		boolean insertmix = false, bicolor = false, isfillormix = false;

		while (input < end) {
			fom_mask = 0;
			code = (compressedData[input++] & 0x000000ff);
			opcode = code >> 4;

			/* Handle different opcode forms */
			switch (opcode) {
			case 0xc:
			case 0xd:
			case 0xe:
				opcode -= 6;
				count = code & 0xf;
				offset = 16;
				break;

			case 0xf:
				opcode = code & 0xf;
				if (opcode < 9) {
					count = (compressedData[input++] & 0xff);
					count |= ((compressedData[input++] & 0xff) << 8);
				} else {
					count = (opcode < 0xb) ? 8 : 1;
				}
				offset = 0;
				break;

			default:
				opcode >>= 1;
				count = code & 0x1f;
				offset = 32;
				break;
			}

			/* Handle strange cases for counts */
			if (offset != 0) {
				isfillormix = ((opcode == 2) || (opcode == 7));

				if (count == 0) {
					if (isfillormix)
						count = (compressedData[input++] & 0x000000ff) + 1;
					else
						count = (compressedData[input++] & 0x000000ff)
								+ offset;
				} else if (isfillormix) {
					count <<= 3;
				}
			}

			switch (opcode) {
			case 0: /* Fill */
				if ((lastopcode == opcode)
						&& !((x == width) && (previous == -1)))
					insertmix = true;
				break;
			case 8: /* Bicolor */
				color1 = cvalx(options, compressedData, input, Bpp);
				// (compressed_pixel[input++]&0x000000ff);
				input += Bpp;
			case 3: /* Color */
				color2 = cvalx(options, compressedData, input, Bpp);
				// color2 = (compressed_pixel[input++]&0x000000ff);
				input += Bpp;
				break;
			case 6: /* SetMix/Mix */
			case 7: /* SetMix/FillOrMix */
				// mix = compressed_pixel[input++];
				mix = cvalx(options, compressedData, input, Bpp);
				input += Bpp;
				opcode -= 5;
				break;
			case 9: /* FillOrMix_1 */
				mask = 0x03;
				opcode = 0x02;
				fom_mask = 3;
				break;
			case 0x0a: /* FillOrMix_2 */
				mask = 0x05;
				opcode = 0x02;
				fom_mask = 5;
				break;

			}

			lastopcode = opcode;
			mixmask = 0;

			/* Output body */
			while (count > 0) {
				if (x >= width) {
					if (height <= 0)
						throw new RdesktopException(
								"Decompressing bitmap failed! Height = "
										+ height);
					x = 0;
					height--;

					previous = line;
					prevY = previous / width;
					line = height * width;
				}

				switch (opcode) {
				case 0: /* Fill */
					if (insertmix) {
						if (previous == -1) {
							// pixel[line+x] = mix;
							callback.setPixel(x, height, mix);
						} else {
							callback.setPixel(x, height, callback.getPixel(x, prevY) ^ mix);
							// pixel[line+x] = (pixel[previous+x] ^ mix);
						}

						insertmix = false;
						count--;
						x++;
					}

					if (previous == -1) {
						while (((count & ~0x7) != 0) && ((x + 8) < width)) {
							for (int i = 0; i < 8; i++) {
								// pixel[line+x] = 0;
								callback.setPixel(x, height, 0);
								count--;
								x++;
							}
						}
						while ((count > 0) && (x < width)) {
							// pixel[line+x] = 0;
							callback.setPixel(x, height, 0);
							count--;
							x++;
						}
					} else {
						while (((count & ~0x7) != 0) && ((x + 8) < width)) {
							for (int i = 0; i < 8; i++) {
								// pixel[line + x] = pixel[previous + x];
								callback.setPixel(x, height, callback.getPixel(x, prevY));
								count--;
								x++;
							}
						}
						while ((count > 0) && (x < width)) {
							// pixel[line + x] = pixel[previous + x];
							callback.setPixel(x, height, callback.getPixel(x, prevY));
							count--;
							x++;
						}
					}
					break;

				case 1: /* Mix */
					if (previous == -1) {
						while (((count & ~0x7) != 0) && ((x + 8) < width)) {
							for (int i = 0; i < 8; i++) {
								// pixel[line + x] = mix;
								callback.setPixel(x, height, mix);
								count--;
								x++;
							}
						}
						while ((count > 0) && (x < width)) {
							// pixel[line + x] = mix;
							callback.setPixel(x, height, mix);
							count--;
							x++;
						}
					} else {

						while (((count & ~0x7) != 0) && ((x + 8) < width)) {
							for (int i = 0; i < 8; i++) {
								// pixel[line + x] = pixel[previous + x] ^ mix;
								callback.setPixel(x, height,
										callback.getPixel(x, prevY) ^ mix);
								count--;
								x++;
							}
						}
						while ((count > 0) && (x < width)) {
							// pixel[line + x] = pixel[previous + x] ^ mix;
							callback.setPixel(x, height,
									callback.getPixel(x, prevY) ^ mix);
							count--;
							x++;
						}

					}
					break;
				case 2: /* Fill or Mix */
					if (previous == -1) {
						while (((count & ~0x7) != 0) && ((x + 8) < width)) {
							for (int i = 0; i < 8; i++) {
								mixmask <<= 1;
								if (mixmask == 0) {
									mask = (fom_mask != 0) ? (byte) fom_mask
											: compressedData[input++];
									mixmask = 1;
								}
								if ((mask & mixmask) != 0) {
									// pixel[line + x] = (byte) mix;
									callback.setPixel(x, height,
													(byte) mix); // XXX Is this cast right?
								} else {
									// pixel[line + x] = 0;
									callback.setPixel(x, height, 0);
								}
								count--;
								x++;
							}
						}
						while ((count > 0) && (x < width)) {
							mixmask <<= 1;
							if (mixmask == 0) {
								mask = (fom_mask != 0) ? (byte) fom_mask
										: compressedData[input++];
								mixmask = 1;
							}
							if ((mask & mixmask) != 0) {
								// pixel[line + x] = mix;
								callback.setPixel(x, height, mix);
							} else {
								// pixel[line + x] = 0;
								callback.setPixel(x, height, 0);
							}
							count--;
							x++;
						}
					} else {
						while (((count & ~0x7) != 0) && ((x + 8) < width)) {
							for (int i = 0; i < 8; i++) {
								mixmask <<= 1;
								if (mixmask == 0) {
									mask = (fom_mask != 0) ? (byte) fom_mask
											: compressedData[input++];
									mixmask = 1;
								}
								if ((mask & mixmask) != 0) {
									// pixel[line + x] = (pixel[previous + x] ^
									// mix);
									callback.setPixel(x, height,
											callback.getPixel(x, prevY) ^ mix);
								} else {
									// pixel[line + x] = pixel[previous + x];
									callback.setPixel(x, height,
											callback.getPixel(x, prevY));
								}
								count--;
								x++;
							}
						}
						while ((count > 0) && (x < width)) {
							mixmask <<= 1;
							if (mixmask == 0) {
								mask = (fom_mask != 0) ? (byte) fom_mask
										: compressedData[input++];
								mixmask = 1;
							}
							if ((mask & mixmask) != 0) {
								// pixel[line + x] = (pixel[previous + x] ^
								// mix);
								callback.setPixel(x, height,
										callback.getPixel(x, prevY) ^ mix);
							} else {
								// pixel[line + x] = pixel[previous + x];
								callback.setPixel(x, height,
										callback.getPixel(x, prevY));
							}
							count--;
							x++;
						}

					}
					break;

				case 3: /* Color */
					while (((count & ~0x7) != 0) && ((x + 8) < width)) {
						for (int i = 0; i < 8; i++) {
							// pixel[line + x] = color2;
							callback.setPixel(x, height, color2);
							count--;
							x++;
						}
					}
					while ((count > 0) && (x < width)) {
						// pixel[line + x] = color2;
						callback.setPixel(x, height, color2);
						count--;
						x++;
					}

					break;

				case 4: /* Copy */
					while (((count & ~0x7) != 0) && ((x + 8) < width)) {
						for (int i = 0; i < 8; i++) {
							// pixel[line + x] = cvalx(compressed_pixel, input,
							// Bpp);
							callback.setPixel(x, height,
									cvalx(options, compressedData, input, Bpp));
							input += Bpp;
							count--;
							x++;
						}
					}
					while ((count > 0) && (x < width)) {
						// pixel[line + x] = cvalx(compressed_pixel, input,
						// Bpp);
						callback.setPixel(x, height,
								cvalx(options, compressedData, input, Bpp));
						input += Bpp;
						count--;
						x++;
					}
					break;

				case 8: /* Bicolor */
					while (((count & ~0x7) != 0) && ((x + 8) < width)) {
						for (int i = 0; i < 8; i++) {
							if (bicolor) {
								// pixel[line + x] = color2;
								callback.setPixel(x, height, color2);
								bicolor = false;
							} else {
								// pixel[line + x] = color1;
								callback.setPixel(x, height, color1);
								bicolor = true;
								count++;
							}
							count--;
							x++;
						}
					}
					while ((count > 0) && (x < width)) {
						if (bicolor) {
							// pixel[line + x] = color2;
							callback.setPixel(x, height, color2);
							bicolor = false;
						} else {
							// pixel[line + x] = color1;
							callback.setPixel(x, height, color1);
							bicolor = true;
							count++;
						}
						count--;
						x++;
					}

					break;

				case 0xd: /* White */
					while (((count & ~0x7) != 0) && ((x + 8) < width)) {
						for (int i = 0; i < 8; i++) {
							// pixel[line + x] = 0xffffff;
							callback.setPixel(x, height, 0xffffff); // XXX The value of white varies
							count--;
							x++;
						}
					}
					while ((count > 0) && (x < width)) {
						// pixel[line + x] = 0xffffff;
						callback.setPixel(x, height, 0xffffff); // XXX
						count--;
						x++;
					}
					break;

				case 0xe: /* Black */
					while (((count & ~0x7) != 0) && ((x + 8) < width)) {
						for (int i = 0; i < 8; i++) {
							// pixel[line + x] = 0x00;
							callback.setPixel(x, height, 0x00); // XXX The value of black changes
							count--;
							x++;
						}
					}
					while ((count > 0) && (x < width)) {
						// pixel[line + x] = 0x00;
						callback.setPixel(x, height, 0x00); // XXX
						count--;
						x++;
					}

					break;
				default:
					throw new RdesktopException(
							"Unimplemented decompress opcode " + opcode);// ;
				}
			}
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
			int size, RdpPacket_Localised data, int Bpp, IndexColorModel cm,
			int left, int top, WrappedImage w) throws RdesktopException {

		byte[] compressed_pixel = new byte[size];
		data.copyToByteArray(compressed_pixel, 0, data.getPosition(), size);
		data.incrementPosition(size);

		decompress(options, width, height, compressed_pixel, Bpp,
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
			RdpPacket_Localised data, int Bpp, IndexColorModel cm)
			throws RdesktopException {

		WrappedImage w;

		byte[] compressed_pixel = new byte[size];
		data.copyToByteArray(compressed_pixel, 0, data.getPosition(), size);
		data.incrementPosition(size);

		if (cm == null)
			w = new WrappedImage(width, height, BufferedImage.TYPE_INT_RGB);
		else
			w = new WrappedImage(width, height, BufferedImage.TYPE_INT_RGB, cm);

		decompress(options, width, height, compressed_pixel, Bpp,
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
			RdpPacket_Localised data, int Bpp) throws RdesktopException {

		byte[] compressed_pixel = new byte[size];
		data.copyToByteArray(compressed_pixel, 0, data.getPosition(), size);
		data.incrementPosition(size);

		int[] pixel = new int[width * height];

		decompress(options, width, height, compressed_pixel, Bpp,
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
			RdpPacket_Localised data, int Bpp) throws RdesktopException {

		byte[] compressed_pixel = new byte[size];
		data.copyToByteArray(compressed_pixel, 0, data.getPosition(), size);
		data.incrementPosition(size);

		byte[] pixel = new byte[width * height];

		decompress(options, width, height, compressed_pixel, Bpp,
				new DecompressionCallback() {
					@Override
					public void setPixel(int x, int y, int color) {
						pixel[(y * width) + x] = (byte) color;
					}

					@Override
					public int getPixel(int x, int y) {
						return (byte) pixel[(y * width) + x];
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
		REGULAR_BG_RUN        (0b000_00000, Type.REGULAR),
		MEGA_MEGA_BG_RUN      (0b1111_0000, Type.MEGA_MEGA),
		REGULAR_FG_RUN        (0b001_00000, Type.REGULAR),
		MEGA_MEGA_FG_RUN      (0b1111_0001, Type.MEGA_MEGA),
		LITE_SET_FG_FG_RUN    (0b1100_0000, Type.LITE),
		MEGA_MEGA_SET_FG_RUN  (0b1111_0110, Type.MEGA_MEGA),
		LITE_DITHERED_RUN     (0b1110_0000, Type.LITE),
		MEGA_MEGA_DITHERED_RUN(0b1111_1000, Type.MEGA_MEGA),
		REGULAR_COLOR_RUN     (0b011_00000, Type.REGULAR),
		MEGA_MEGA_COLOR_RUN   (0b1111_0011, Type.MEGA_MEGA),
		REGULAR_FGBG_IMAGE    (0b010_00000, Type.REG_FGBG),
		MEGA_MEGA_FGBG_IMAGE  (0b1111_0010, Type.MEGA_MEGA),
		LITE_SET_FG_FGBG_IMAGE(0b1101_0000, Type.LITE),
		REGULAR_COLOR_IMAGE   (0b100_00000, Type.REGULAR),
		MEGA_MEGA_COLOR_IMAGE (0b1111_0100, Type.MEGA_MEGA),
		SPECIAL_FGBG_1        (0b1111_1001, Type.SINGLE_BYTE),
		SPECIAL_FGBG_2        (0b1111_1010, Type.SINGLE_BYTE),
		WHITE                 (0b1111_1101, Type.SINGLE_BYTE),
		BLACK                 (0b1111_1110, Type.SINGLE_BYTE);

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
		public int getLength(int start, RdpPacket_Localised data) {
			return type.getLength(start, data);
		}

		public static enum Type {
			REGULAR(0b11100000) {
				@Override
				public int getLength(int start, RdpPacket_Localised data) {
					int len = start & REG_MASK;
					if (len != 0) {
						logger.trace("Regular - len={}", len);
						return len;
					} else {
						// MEGA run; 1 extra byte
						int num = data.get8();
						int val = num + REG_COUNT;
						logger.trace("Regular MEGA - read={} => {}", num, val);
						return val;
					}
				}
			},
			LITE(0b11110000) {
				@Override
				public int getLength(int start, RdpPacket_Localised data) {
					int len = start & LITE_MASK;
					if (len != 0) {
						logger.trace("Lite - len={}", len);
						return len;
					} else {
						// MEGA run; 1 extra byte
						int num = data.get8();
						int val = num + LITE_COUNT;
						logger.trace("Lite MEGA - read={} => {}", num, val);
						return val;
					}
				}
			},
			MEGA_MEGA(0b11111111) {
				@Override
				public int getLength(int start, RdpPacket_Localised data) {
					int value = data.getLittleEndian16();
					logger.trace("MEGA MEGA - read={}", value);
					return value;
				}
			},
			SINGLE_BYTE(0b11111111) {
				@Override
				public int getLength(int start, RdpPacket_Localised data) {
					logger.trace("Single byte");
					return 0;
				}
			},
			REG_FGBG(0b11100000) {
				@Override
				public int getLength(int start, RdpPacket_Localised data) {
					int len = start & REG_MASK;
					if (len != 0) {
						int val = len * 8;
						logger.trace("Regular FGBG - len={} => {}", len, val);
						return val;
					} else {
						int read = data.get8();
						int val = read + 1; // Yes, + 1, not another number
						logger.trace("\"MEGA\" Regular FGBG - read={} => {}", read, val);
						return val;
					}
				}
			},
			LITE_FGBG(0b11110000) {
				@Override
				public int getLength(int start, RdpPacket_Localised data) {
					int len = start & LITE_MASK;
					if (len != 0) {
						int val = len * 8;
						logger.trace("Lite FGBG - len={} => {}", len, val);
						return val;
					} else {
						int read = data.get8();
						int val = read + 1; // Again, + 1
						logger.trace("\"MEGA\" Lite  FGBG - read={} => {}", read, val);
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

			public abstract int getLength(int start, RdpPacket_Localised data);
		}
	}
}

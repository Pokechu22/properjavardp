/* RdesktopCanvas.java
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

import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.MemoryImageSource;

import net.propero.rdp.keymapping.KeyCode;
import net.propero.rdp.keymapping.KeyCode_FileBased;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Canvas component, handles drawing requests from server, and passes user input
 * to Input class.
 */
public class RdesktopCanvas extends Canvas {
	private static final long serialVersionUID = -6806580381785981945L;

	private static final Logger logger = LogManager.getLogger();

	OrderSurface surface;

	// Graphics backstore_graphics;

	private Cursor previous_cursor = null; // for setBusyCursor and

	// unsetBusyCursor

	private Input input = null;

	private Robot robot = null;

	public KeyCode keys = null;

	public KeyCode_FileBased fbKeys = null;

	public String sKeys = null;

	// private int[] colors = null; // needed for integer backstore

	public Rdp rdp = null;

	// protected int[] backstore_int = null;
	protected final Options options;

	/**
	 * Initialise this canvas to specified width and height, also initialise
	 * backstore
	 *
	 * @param width
	 *            Desired width of canvas
	 * @param height
	 *            Desired height of canvas
	 */
	public RdesktopCanvas(Options options, int width, int height) {
		super();
		this.options = options;
		setSize(width, height);

		this.surface = new OrderSurface(options, width, height);

		// now do input listeners in registerCommLayer() / registerKeyboard()
	}

	@Override
	public void paint(Graphics g) {
		update(g);
	}

	@Override
	public void addNotify() {
		super.addNotify();

		if (robot == null) {
			try {
				robot = new Robot();
			} catch (AWTException e) {
				logger.warn("Pointer movement not allowed", e);
			}
		}
	}

	@Override
	public void update(Graphics g) {
		Rectangle r = g.getClipBounds();
		g.drawImage(surface.getSubimage(r.x, r.y, r.width, r.height), r.x,
				r.y, null);
	}

	/**
	 * Register the Rdp layer to act as the communications interface to this
	 * canvas
	 *
	 * @param rdp
	 *            Rdp object controlling Rdp layer communication
	 */
	public void registerCommLayer(Rdp rdp) {
		this.rdp = rdp;
		if (fbKeys != null) {
			input = new Input(options, this, rdp, fbKeys);
		}
	}

	/**
	 * Register keymap
	 *
	 * @param keys
	 *            Keymapping object for use in handling keyboard events
	 */
	public void registerKeyboard(KeyCode_FileBased keys) {
		this.fbKeys = keys;
		if (rdp != null) {
			// rdp and keys have been registered...
			input = new Input(options, this, rdp, keys);
		}
	}

	/**
	 * Move the mouse pointer (only available in Java 1.3+)
	 *
	 * @param x
	 *            x coordinate for mouse move
	 * @param y
	 *            y coordinate for mouse move
	 */
	public void movePointer(int x, int y) {
		Point p = this.getLocationOnScreen();
		x = x + p.x;
		y = y + p.y;
		robot.mouseMove(x, y);
	}

	/**
	 * Create an AWT Cursor object
	 *
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param andmask
	 * @param xormask
	 * @param cache_idx
	 * @return Created Cursor
	 */
	public Cursor createCursor(int x, int y, int w, int h, byte[] andmask,
			byte[] xormask, int cache_idx) {
		int pxormask = 0;
		int pandmask = 0;
		Point p = new Point(x, y);
		int size = w * h;
		int scanline = w / 8;
		int offset = 0;
		byte[] mask = new byte[size];
		int[] cursor = new int[size];
		int pcursor = 0, pmask = 0;

		offset = size;

		for (int i = 0; i < h; i++) {
			offset -= w;
			pmask = offset;
			for (int j = 0; j < scanline; j++) {
				for (int bit = 0x80; bit > 0; bit >>= 1) {
					if ((andmask[pandmask] & bit) != 0) {
						mask[pmask] = 0;
					} else {
						mask[pmask] = 1;
					}
					pmask++;
				}
				pandmask++;
			}
		}

		offset = size;
		pcursor = 0;

		for (int i = 0; i < h; i++) {
			offset -= w;
			pcursor = offset;
			for (int j = 0; j < w; j++) {
				cursor[pcursor] = ((xormask[pxormask + 2] << 16) & 0x00ff0000)
						| ((xormask[pxormask + 1] << 8) & 0x0000ff00)
						| (xormask[pxormask] & 0x000000ff);
				pxormask += 3;
				pcursor++;
			}

		}

		offset = size;
		pmask = 0;
		pcursor = 0;
		pxormask = 0;

		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				if ((mask[pmask] == 0) && (cursor[pcursor] != 0)) {
					cursor[pcursor] = ~(cursor[pcursor]);
					cursor[pcursor] |= 0xff000000;
				} else if ((mask[pmask] == 1) || (cursor[pcursor] != 0)) {
					cursor[pcursor] |= 0xff000000;
				}
				pcursor++;
				pmask++;
			}
		}

		Image wincursor = this.createImage(new MemoryImageSource(w, h, cursor,
				0, w));
		return createCustomCursor(wincursor, p, "", cache_idx);
	}

	/**
	 * Create an AWT Cursor from an image
	 *
	 * @param wincursor
	 * @param p
	 * @param s
	 * @param cache_idx
	 * @return Generated Cursor object
	 */
	protected Cursor createCustomCursor(Image wincursor, Point p, String s,
			int cache_idx) {
		// TODO: This doesn't do anything with the cache - is that right?
		/*if (cache_idx == 1)
			return Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
		return Cursor.getDefaultCursor();*/
		return Toolkit.getDefaultToolkit().createCustomCursor(wincursor, p, "");
	}

	/**
	 * Handle the window losing focus, notify input classes
	 */
	public void lostFocus() {
		if (input != null) {
			input.lostFocus();
		}
	}

	/**
	 * Handle the window gaining focus, notify input classes
	 */
	public void gainedFocus() {
		if (input != null) {
			input.gainedFocus();
		}
	}

	/**
	 * Notify the input classes that the connection is ready for sending
	 * messages
	 */
	public void triggerReadyToSend() {
		input.triggerReadyToSend();
	}

	/**
	 * Notifies the canvas that the size changed.
	 */
	public void sizeChanged() {
		this.setSize(options.width, options.height);
		this.surface.sizeChanged();
	}
}

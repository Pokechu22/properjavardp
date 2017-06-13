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

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Rectangle;

import net.propero.rdp.keymapping.KeyCode;
import net.propero.rdp.keymapping.KeyCode_FileBased;

/**
 * Canvas component, handles drawing requests from server, and passes user input
 * to Input class.
 */
public class RdesktopCanvas extends Canvas {
	private static final long serialVersionUID = -6806580381785981945L;

	OrderSurface surface;

	private Input input = null;

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
}

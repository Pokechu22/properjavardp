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
package net.propero.rdp.ui;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.propero.rdp.Input;
import net.propero.rdp.Options;
import net.propero.rdp.OrderSurface;
import net.propero.rdp.Rdp;
import net.propero.rdp.keymapping.KeyCode;
import net.propero.rdp.keymapping.KeyCode_FileBased;

/**
 * Canvas component, handles drawing requests from server, and passes user input
 * to Input class.
 */
public class RdesktopCanvas extends Canvas {
	private static final Logger LOGGER = LogManager.getLogger();

	private static final long serialVersionUID = -6806580381785981945L;

	private OrderSurface surface;

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

		this.setFocusTraversalKeysEnabled(false);
		// now do input listeners in registerCommLayer() / registerKeyboard()
	}

	@Override
	public void paint(Graphics g) {
		update(g);
	}

	@Override
	public void update(Graphics g) {
		if (surface == null) {
			return;
		}
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
			input = new Input(options, rdp, fbKeys);
			registerListeners();
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
			input = new Input(options, rdp, keys);
			registerListeners();
		}
	}

	private void registerListeners() {
		this.addMouseListener(new RdesktopMouseAdapter());
		this.addMouseMotionListener(new RdesktopMouseMotionAdapter());
		this.addKeyListener(new RdesktopKeyAdapter());
		this.addMouseWheelListener(new RdesktopMouseWheelAdapter());
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

	public void registerSurface(OrderSurface surface) {
		this.surface = surface;
		this.setSize(surface.getWidth(), surface.getHeight());
	}

	private class RdesktopKeyAdapter extends KeyAdapter {

		private final Set<Integer> pressedKeys = new HashSet<>();

		/**
		 * Handle a keyPressed event, sending any relevant keypresses to the
		 * server
		 */
		@Override
		public void keyPressed(KeyEvent e) {
			input.lastKeyEvent = e;
			input.modifiersValid = true;
			long time = Input.getTime();

			// Some java versions have keys that don't generate keyPresses -
			// here we add the key so we can later check if it happened
			pressedKeys.add(e.getKeyCode());

			LOGGER.debug("PRESSED keychar='" + e.getKeyChar() + "' keycode=0x"
					+ Integer.toHexString(e.getKeyCode()) + " char='"
					+ ((char) e.getKeyCode()) + "'");

			if (rdp != null) {
				if (!input.handleSpecialKeys(time, e, true)) {
					input.sendKeyPresses(input.newKeyMapper.getKeyStrokes(e));
				}
				// sendScancode(time, RDP_KEYPRESS, keys.getScancode(e));
			}
		}

		/**
		 * Handle a keyTyped event, sending any relevant keypresses to the
		 * server
		 */
		@Override
		public void keyTyped(KeyEvent e) {
			input.lastKeyEvent = e;
			input.modifiersValid = true;
			long time = Input.getTime();

			// Some java versions have keys that don't generate keyPresses -
			// here we add the key so we can later check if it happened
			pressedKeys.add(e.getKeyCode());

			LOGGER.debug("TYPED keychar='" + e.getKeyChar() + "' keycode=0x"
					+ Integer.toHexString(e.getKeyCode()) + " char='"
					+ ((char) e.getKeyCode()) + "'");

			if (rdp != null) {
				if (!input.handleSpecialKeys(time, e, true))
				{
					input.sendKeyPresses(input.newKeyMapper.getKeyStrokes(e));
					// sendScancode(time, RDP_KEYPRESS, keys.getScancode(e));
				}
			}
		}

		/**
		 * Handle a keyReleased event, sending any relevent key events to the
		 * server
		 */
		@Override
		public void keyReleased(KeyEvent e) {
			// Some java versions have keys that don't generate keyPresses -
			// we added the key to the vector in keyPressed so here we check for
			// it
			if (!pressedKeys.contains(e.getKeyCode())) {
				this.keyPressed(e);
			}

			pressedKeys.remove(e.getKeyCode());

			input.lastKeyEvent = e;
			input.modifiersValid = true;
			long time = Input.getTime();

			LOGGER.debug("RELEASED keychar='" + e.getKeyChar() + "' keycode=0x"
					+ Integer.toHexString(e.getKeyCode()) + " char='"
					+ ((char) e.getKeyCode()) + "'");
			if (rdp != null) {
				if (!input.handleSpecialKeys(time, e, false)) {
					input.sendKeyPresses(input.newKeyMapper.getKeyStrokes(e));
					// sendScancode(time, RDP_KEYRELEASE, keys.getScancode(e));
				}
			}
		}
	}

	private class RdesktopMouseAdapter extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent e) {
			int button = e.getButton();
			if (button == MouseEvent.NOBUTTON) {
				return;
			}

			if (button == MouseEvent.BUTTON3) { // Java treats right mouse as button 3
				button = 2;
			} else if (button == MouseEvent.BUTTON2) { // and middle mouse as button 2
				button = 3;
			}
			if (input.canSendButton(button)) {
				input.mouseButton(button, true, getX(e), getY(e));
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			int button = e.getButton();
			if (button == MouseEvent.NOBUTTON) {
				return;
			}

			if (button == MouseEvent.BUTTON3) { // Java treats right mouse as button 3
				button = 2;
			} else if (button == MouseEvent.BUTTON2) { // and middle mouse as button 2
				button = 3;
			}
			if (input.canSendButton(button)) {
				input.mouseButton(button, false, getX(e), getY(e));
			}
		}
	}

	private class RdesktopMouseMotionAdapter extends MouseMotionAdapter {
		@Override
		public void mouseMoved(MouseEvent e) {
			input.moveMouse(getX(e), getY(e));
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			input.moveMouse(getX(e), getY(e));
		}
	}

	private static final int SCROLL_DEFAULT_SIZE = 0x80;

	private class RdesktopMouseWheelAdapter implements MouseWheelListener {
		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			// Right now, we only use a fixed size for scroll.
			if (e.getWheelRotation() < 0) { // negative values are up
				// ... which is sent as a positive value.
				input.scrollVertically(+SCROLL_DEFAULT_SIZE);
			} else {
				// and positive values are down, which is sent as a negative value.
				input.scrollVertically(-SCROLL_DEFAULT_SIZE);
			}
		}
	}

	/**
	 * Gets the normalized x coordinate from the given mouse event.
	 */
	private int getX(MouseEvent e) {
		return e.getX();
	}

	/**
	 * Gets the normalized y coordinate from the given mouse event.
	 */
	private int getY(MouseEvent e) {
		return e.getY();
	}
}

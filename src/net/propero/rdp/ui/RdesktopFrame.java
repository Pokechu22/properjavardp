/* RdesktopFrame.java
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

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.MemoryImageSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.propero.rdp.Options;
import net.propero.rdp.OrderSurface;
import net.propero.rdp.Rdesktop;
import net.propero.rdp.Rdp;
import net.propero.rdp.api.InitState;
import net.propero.rdp.api.RdesktopCallback;
import net.propero.rdp.api.SystemCursorType;
import net.propero.rdp.keymapping.KeyCode_FileBased;
import net.propero.rdp.rdp5.VChannels;
import net.propero.rdp.rdp5.cliprdr.ClipChannel;

/**
 * Window for RDP session
 */
public class RdesktopFrame extends Frame implements RdesktopCallback {

	private static final long serialVersionUID = -886909197782887125L;

	private static final Logger LOGGER = LogManager.getLogger();

	public RdesktopCanvas canvas = null;

	public Rdp rdp = null;

	public RdpMenu menu = null;

	private Robot robot = null;

	/** An invisible cursor.  Null until initialized. */
	@Nullable
	private Cursor invisibleCursor = null;

	/**
	 * Register the clipboard channel
	 *
	 * @param c
	 *            ClipChannel object for controlling clipboard mapping
	 */
	public void setClip(ClipChannel c) {
		canvas.addFocusListener(c);
	}

	@Override
	public boolean action(Event event, Object arg) {
		if (menu != null) {
			return menu.action(event, arg);
		}
		return false;
	}

	@Override
	public void addNotify() {
		super.addNotify();

		if (robot == null) {
			try {
				robot = new Robot();
			} catch (AWTException e) {
				LOGGER.warn("Pointer movement not allowed", e);
			}
		}
	}

	protected boolean inFullscreen = false;

	public void goFullScreen() {
		if (!options.fullscreen) {
			return;
		}

		inFullscreen = true;

		if (this.isDisplayable()) {
			this.dispose();
		}
		this.setVisible(false);
		this.setLocation(0, 0);
		this.setUndecorated(true);
		this.setVisible(true);
		// setExtendedState (Frame.MAXIMIZED_BOTH);
		// GraphicsEnvironment env =
		// GraphicsEnvironment.getLocalGraphicsEnvironment();
		// GraphicsDevice myDevice = env.getDefaultScreenDevice();
		// if (myDevice.isFullScreenSupported())
		// myDevice.setFullScreenWindow(this);

		this.pack();
	}

	public void leaveFullScreen() {
		if (!options.fullscreen) {
			return;
		}

		inFullscreen = false;

		if (this.isDisplayable()) {
			this.dispose();
		}

		GraphicsEnvironment env = GraphicsEnvironment
				.getLocalGraphicsEnvironment();
		GraphicsDevice myDevice = env.getDefaultScreenDevice();
		if (myDevice.isFullScreenSupported()) {
			myDevice.setFullScreenWindow(null);
		}

		this.setLocation(10, 10);
		this.setUndecorated(false);
		this.setVisible(true);
		// setExtendedState (Frame.NORMAL);
		this.pack();
	}

	/**
	 * Switch in/out of fullscreen mode
	 */
	public void toggleFullScreen() {
		if (inFullscreen) {
			leaveFullScreen();
		} else {
			goFullScreen();
		}
	}

	private boolean menuVisible = false;

	/**
	 * Display the menu bar
	 */
	public void showMenu() {
		if (menu == null) {
			menu = new RdpMenu(options, this);
		}

		if (!menuVisible && options.enable_menu) {
			this.setMenuBar(menu);
		}
		canvas.repaint();
		menuVisible = true;
	}

	/**
	 * Hide the menu bar
	 */
	public void hideMenu() {
		if (menuVisible && options.enable_menu) {
			this.setMenuBar(null);
		}
		// canvas.setSize(this.WIDTH, this.HEIGHT);
		canvas.repaint();
		menuVisible = false;
	}

	/**
	 * Toggle the menu on/off (show if hidden, hide if visible)
	 *
	 */
	public void toggleMenu() {
		if (!menuVisible) {
			showMenu();
		} else {
			hideMenu();
		}
	}

	protected final Options options;

	/**
	 * Create a new RdesktopFrame. Size defined by options.width and
	 * options.height Creates RdesktopCanvas occupying entire frame
	 */
	public RdesktopFrame(Options options) {
		super();
		this.options = options;
		this.canvas = new RdesktopCanvas(options, options.width,
				options.height);
		add(this.canvas);
		this.setSize(options.width, options.height);
		setTitle(options.windowTitle);

		if (options.os == Options.OS.WINDOWS)
		{
			setResizable(false);
			// Windows has to setResizable(false) before pack,
			// else draws on the frame
		}

		if (options.fullscreen) {
			goFullScreen();
			pack();
			setLocation(0, 0);
		} else {// centre
			pack();
			centreWindow();
		}

		if (options.os != Options.OS.WINDOWS)
		{
			setResizable(false);
			// Linux Java 1.3 needs pack() before setResizeable
		}

		addWindowListener(new RdesktopWindowAdapter());
		canvas.addFocusListener(new RdesktopFocusListener());
		if (options.os == Options.OS.WINDOWS) {
			// redraws screen on window move
			addComponentListener(new RdesktopComponentAdapter());
		}

		canvas.requestFocus();
		this.setFocusTraversalKeysEnabled(false);
	}

	/**
	 * Register the RDP communications layer with this frame
	 *
	 * @param rdp
	 *            Rdp object encapsulating the RDP comms layer
	 */
	public void registerCommLayer(Rdp rdp) {
		this.rdp = rdp;
		canvas.registerCommLayer(rdp);
	}

	/**
	 * Register keymap
	 *
	 * @param keys
	 *            Keymapping object for use in handling keyboard events
	 */
	public void registerKeyboard(KeyCode_FileBased keys) {
		canvas.registerKeyboard(keys);
	}

	class RdesktopFocusListener implements FocusListener {

		@Override
		public void focusGained(FocusEvent arg0) {
			if (options.os == Options.OS.WINDOWS) {
				// canvas.repaint();
				canvas.repaint(0, 0, options.width, options.height);
			}
			// gained focus..need to check state of locking keys
			canvas.gainedFocus();
		}

		@Override
		public void focusLost(FocusEvent arg0) {
			// lost focus - need clear keys that are down
			canvas.lostFocus();
		}
	}

	class RdesktopWindowAdapter extends WindowAdapter {

		@Override
		public void windowClosing(WindowEvent e) {
			hide();
			Rdesktop.exit(0, rdp, (RdesktopFrame) e.getWindow(), true);
		}

		@Override
		public void windowLostFocus(WindowEvent e) {
			LOGGER.info("windowLostFocus");
			// lost focus - need clear keys that are down
			canvas.lostFocus();
		}

		@Override
		public void windowDeiconified(WindowEvent e) {
			if (options.os == Options.OS.WINDOWS) {
				// canvas.repaint();
				canvas.repaint(0, 0, options.width, options.height);
			}
			canvas.gainedFocus();
		}

		@Override
		public void windowActivated(WindowEvent e) {
			if (options.os == Options.OS.WINDOWS) {
				// canvas.repaint();
				canvas.repaint(0, 0, options.width, options.height);
			}
			// gained focus..need to check state of locking keys
			canvas.gainedFocus();
		}

		@Override
		public void windowGainedFocus(WindowEvent e) {
			if (options.os == Options.OS.WINDOWS) {
				// canvas.repaint();
				canvas.repaint(0, 0, options.width, options.height);
			}
			// gained focus..need to check state of locking keys
			canvas.gainedFocus();
		}
	}

	class RdesktopComponentAdapter extends ComponentAdapter {
		@Override
		public void componentMoved(ComponentEvent e) {
			canvas.repaint(0, 0, options.width, options.height);
		}
	}

	class YesNoDialog extends Dialog implements ActionListener {

		private static final long serialVersionUID = 5491261266068232056L;

		Button yes, no;

		boolean retry = false;

		public YesNoDialog(Frame parent, String title, String[] message) {
			super(parent, title, true);
			// Box msg = Box.createVerticalBox();
			// for(int i=0; i<message.length; i++) msg.add(new
			// Label(message[i],Label.CENTER));
			// this.add("Center",msg);
			Panel msg = new Panel();
			msg.setLayout(new GridLayout(message.length, 1));
			for (int i = 0; i < message.length; i++) {
				msg.add(new Label(message[i], Label.CENTER));
			}
			this.add("Center", msg);

			Panel p = new Panel();
			p.setLayout(new FlowLayout());
			yes = new Button("Yes");
			yes.addActionListener(this);
			p.add(yes);
			no = new Button("No");
			no.addActionListener(this);
			p.add(no);
			this.add("South", p);
			this.pack();
			if (getSize().width < 240) {
				setSize(new Dimension(240, getSize().height));
			}

			centreWindow(this);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (e.getSource() == yes) {
				retry = true;
			} else {
				retry = false;
			}
			this.hide();
			this.dispose();
		}
	}

	class OKDialog extends Dialog implements ActionListener {
		private static final long serialVersionUID = 100978821816327378L;

		public OKDialog(Frame parent, String title, String[] message) {

			super(parent, title, true);
			// Box msg = Box.createVerticalBox();
			// for(int i=0; i<message.length; i++) msg.add(new
			// Label(message[i],Label.CENTER));
			// this.add("Center",msg);

			Panel msg = new Panel();
			msg.setLayout(new GridLayout(message.length, 1));
			for (int i = 0; i < message.length; i++) {
				msg.add(new Label(message[i], Label.CENTER));
			}
			this.add("Center", msg);

			Panel p = new Panel();
			p.setLayout(new FlowLayout());
			Button ok = new Button("OK");
			ok.addActionListener(this);
			p.add(ok);
			this.add("South", p);
			this.pack();

			if (getSize().width < 240) {
				setSize(new Dimension(240, getSize().height));
			}

			centreWindow(this);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			this.hide();
			this.dispose();
		}
	}

	/**
	 * Display an error dialog with "Yes" and "No" buttons and the title
	 * "properJavaRDP error"
	 *
	 * @param msg
	 *            Array of message lines to display in dialog box
	 * @return True if "Yes" was clicked to dismiss box
	 */
	public boolean showYesNoErrorDialog(String[] msg) {

		YesNoDialog d = new YesNoDialog(this, "properJavaRDP error", msg);
		d.show();
		return d.retry;
	}

	/**
	 * Display an error dialog with the title "properJavaRDP error"
	 *
	 * @param msg
	 *            Array of message lines to display in dialog box
	 */
	public void showErrorDialog(String[] msg) {
		Dialog d = new OKDialog(this, "properJavaRDP error", msg);
		d.show();
	}

	@Override
	public void stateChanged(InitState state) {
		if (state == InitState.READY_TO_SEND) {
			this.show();
			canvas.triggerReadyToSend();
		}
	}

	/**
	 * Centre a window to the screen
	 *
	 * @param f
	 *            Window to be centred
	 */
	public void centreWindow(Window f) {
		Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension window_size = f.getSize();
		int x = (screen_size.width - window_size.width) / 2;
		if (x < 0)
		{
			x = 0; // window can be bigger than screen
		}
		int y = (screen_size.height - window_size.height) / 2;
		if (y < 0)
		{
			y = 0; // window can be bigger than screen
		}
		f.setLocation(x, y);
	}

	/**
	 * Centre this window
	 */
	public void centreWindow() {
		centreWindow(this);
	}

	/**
	 * Move the mouse pointer (only available in Java 1.3+)
	 *
	 * @param x
	 *            x coordinate for mouse move
	 * @param y
	 *            y coordinate for mouse move
	 */
	@Override
	public void movePointer(int x, int y) {
		Point p = this.getLocationOnScreen();
		x = x + p.x;
		y = y + p.y;
		robot.mouseMove(x, y);
	}

	@Override
	public Cursor createCursor(int hotspotX, int hotspotY, int width, int height, byte[] andmask,
			byte[] xormask) {
		Point p = new Point(hotspotX, hotspotY);
		final int size = width * height;
		final int scanline = width / 8;
		boolean[] mask = new boolean[size];
		int[] cursor = new int[size];

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < scanline; x++) {
				int andIndex = (height - y - 1) * scanline + x;

				for (int bit = 0; bit < 8; bit++) {
					int maskIndex = ((y * scanline) + x) * 8 + bit;
					int bitmask = 0x80 >> bit;

					if ((andmask[andIndex] & bitmask) != 0) {
						mask[maskIndex] = true;
					} else {
						mask[maskIndex] = false;
					}
				}
			}
		}

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int xorIndex = ((height - y - 1) * width + x) * 3;
				int cursorIndex = y * width + x;
				cursor[cursorIndex] = ((xormask[xorIndex + 2] << 16) & 0x00ff0000)
						| ((xormask[xorIndex + 1] << 8) & 0x0000ff00)
						| (xormask[xorIndex] & 0x000000ff);
			}

		}

		for (int i = 0; i < size; i++) {
			if ((mask[i] == true) && (cursor[i] != 0)) {
				cursor[i] = ~(cursor[i]);
				cursor[i] |= 0xff000000;
			} else if ((mask[i] == false) || (cursor[i] != 0)) {
				cursor[i] |= 0xff000000;
			}
		}

		Image wincursor = this.createImage(new MemoryImageSource(width, height, cursor,
				0, width));
		return createCustomCursor(wincursor, p, "");
	}

	@Override
	public void setCursor(@Nonnull Object cursor) {
		if (cursor instanceof Cursor) {
			setCursor((Cursor) cursor);
		} else if (cursor instanceof SystemCursorType) {
			switch ((SystemCursorType)cursor) {
			case INVISIBLE_CURSOR: {
				if (invisibleCursor == null) {
					invisibleCursor = createCustomCursor(
							new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB),
							new Point(1, 1), "");
				}
				setCursor(invisibleCursor);
				break;
			}
			case DEFAULT_CURSOR:
			{
				super.setCursor((Cursor) null);
				break;
			}
			default: {
				throw new IllegalArgumentException("Unknown SystemCursorType " + cursor);
			}
			}
		} else {
			throw new IllegalArgumentException("Unexpected object " + cursor + " (" + (cursor != null ? cursor.getClass() : null) + ")");
		}
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
	protected Cursor createCustomCursor(Image wincursor, Point p, String s) {
		// TODO: This doesn't do anything with the cache - is that right?
		/*if (cache_idx == 1)
			return Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
		return Cursor.getDefaultCursor();*/
		return Toolkit.getDefaultToolkit().createCustomCursor(wincursor, p, "");
	}

	@Override
	public void error(Exception ex, Rdp rdp) {
		Rdesktop.error(ex, rdp, this, true);
	}

	@Override
	public void sizeChanged(int width, int height) {
		this.setSize(width, height);
		canvas.setSize(width, height);
	}

	@Override
	public void registerSurface(OrderSurface surface) {
		canvas.registerSurface(surface);
	}

	@Override
	public void markDirty(int x, int y, int width, int height) {
		canvas.repaint(x, y, width, height);
	}

	@Override
	public void registerChannels(VChannels vchannels) {
		ClipChannel clipChannel = new ClipChannel(options);

		// TODO: implement all relevant channels
		if (options.map_clipboard) {
			vchannels.register(clipChannel);
		}
	}
}

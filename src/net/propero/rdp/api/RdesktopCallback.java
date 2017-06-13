package net.propero.rdp.api;

import java.awt.Cursor;

import net.propero.rdp.RdesktopCanvas;
import net.propero.rdp.Rdp;

public interface RdesktopCallback {
	/**
	 * Notify the canvas that the connection is ready for sending messages
	 */
	public abstract void triggerReadyToSend();

	/**
	 * Retrieve the canvas associated with this.
	 *
	 * @return RdesktopCanvas object associated with this frame (non-null)
	 */
	public abstract RdesktopCanvas getCanvas();

	/**
	 * Kill the program with the given exception
	 * @param ex
	 * @param rdp
	 */
	public abstract void error(Exception ex, Rdp rdp);

	public abstract void movePointer(int x, int y);
	/**
	 * Create an AWT Cursor object
	 *
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param andmask
	 * @param xormask
	 * @param cache_idx XXX this is currently unused
	 * @return Created Cursor
	 */
	public abstract Cursor createCursor(int x, int y, int w, int h, byte[] andmask,
			byte[] xormask, int cache_idx);
	public abstract void setCursor(Cursor cursor);
	public abstract Cursor getCursor();

	/**
	 * Callback when the underlying image's size has changed
	 * @param newWidth The new width of the image
	 * @param newHeight The new height of the image
	 */
	public abstract void sizeChanged(int newWidth, int newHeight);
}

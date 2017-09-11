package net.propero.rdp.api;

import javax.annotation.Nonnull;

import net.propero.rdp.OrderSurface;
import net.propero.rdp.Rdp;
import net.propero.rdp.rdp5.VChannel;
import net.propero.rdp.rdp5.VChannels;

public interface RdesktopCallback {
	/**
	 * Notification that the current state has changed and some actions are now possible.
	 *
	 * @param state The new state
	 */
	public abstract void stateChanged(InitState state);

	public abstract void markDirty(int x, int y, int width, int height);
	public abstract void registerSurface(OrderSurface surface);

	/**
	 * Kill the program with the given exception
	 * @param ex
	 * @param rdp
	 */
	public abstract void error(Exception ex, Rdp rdp);

	public abstract void movePointer(int x, int y);
	
	/**
	 * Creates a new custom cursor. All fields are as described in [MS-RDPBCGR]
	 * section 2.2.9.1.1.4.4.
	 *
	 * @param hotspotX
	 *            The x coordinate of the "hot spot" of the cursor
	 * @param hotspotY
	 *            The y coordinate of the "hot spot" of the cursor
	 * @param width
	 *            The width of the cursor
	 * @param height
	 *            The height of the cursor
	 * @param andmask
	 *            Contains the 24-bpp, bottom-up XOR mask scan-line data. The
	 *            XOR mask is padded to a 2-byte boundary for each encoded
	 *            scan-line. For example, if a 3x3 pixel cursor is being sent,
	 *            then each scan-line will consume 10 bytes (3 pixels per
	 *            scan-line multiplied by 3 bytes per pixel, rounded up to the
	 *            next even number of bytes).
	 *            <p>
	 *            In essence, this is the color data.
	 * @param xormask
	 *            Contains the 1-bpp, bottom-up AND mask scan-line data. The AND
	 *            mask is padded to a 2-byte boundary for each encoded
	 *            scan-line. For example, if a 7x7 pixel cursor is being sent,
	 *            then each scan-line will consume 2 bytes (7 pixels per
	 *            scan-line multiplied by 1 bpp, rounded up to the next even
	 *            number of bytes).
	 *            <p>
	 *            In essence, this is the alpha data.
	 * @return Created Cursor
	 */
	@Nonnull
	public abstract Object createCursor(int hotspotX, int hotspotY, int width, int height, byte[] andmask, byte[] xormask);
	
	/**
	 * Sets the displayed cursor. This method will only be called with objects
	 * that are returned by {@link #createCursor}.
	 * 
	 * @param cursor
	 *            The cursor to display.
	 */
	public abstract void setCursor(@Nonnull Object cursor);

	/**
	 * Callback when the underlying image's size has changed
	 * @param newWidth The new width of the image
	 * @param newHeight The new height of the image
	 */
	public abstract void sizeChanged(int newWidth, int newHeight);

	/**
	 * Callback where {@link VChannel}s may be registered.
	 *
	 * @param vchannels Channel controller to register to
	 */
	public abstract void registerChannels(VChannels vchannels);
}

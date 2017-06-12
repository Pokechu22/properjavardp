package net.propero.rdp.api;

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
}

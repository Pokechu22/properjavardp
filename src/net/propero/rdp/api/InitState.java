package net.propero.rdp.api;

public enum InitState {
	/**
	 * RDP has just started.
	 */
	INIT,
	/**
	 * Log-in has completed.
	 */
	LOGGED_ON,
	/**
	 * Everything is fully connected.
	 */
	READY_TO_SEND
}
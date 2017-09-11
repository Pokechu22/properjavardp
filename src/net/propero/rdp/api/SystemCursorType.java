package net.propero.rdp.api;

import javax.annotation.Nonnull;

/**
 * Special built-in cursors.
 *
 * @see [MS-RDPBCGR] 2.2.9.1.1.4.3
 */
public enum SystemCursorType {
	/**
	 * The hidden pointer.
	 */
	INVISIBLE_CURSOR(0x00000000),
	/**
	 * The default system pointer.
	 */
	DEFAULT_CURSOR(0x00007F00);

	/**
	 * The ID associated with this cursor over the network.
	 */
	public final int id;

	private SystemCursorType(int id) {
		this.id = id;
	}

	/**
	 * Gets a SystemCursorType by network ID
	 *
	 * @param id The network ID
	 * @return The type
	 * @throws IllegalArgumentException if there is no SystemCursorType with that ID
	 */
	@Nonnull
	public static SystemCursorType byId(int id) throws IllegalArgumentException {
		for (SystemCursorType type : values()) {
			if (type.id == id) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown cursor type " + id + " (0x" + Integer.toHexString(id) + ")");
	}
}

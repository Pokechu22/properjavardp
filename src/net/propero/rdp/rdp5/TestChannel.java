/* TestChannel.java
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
package net.propero.rdp.rdp5;

import net.propero.rdp.Options;
import net.propero.rdp.RdpPacket;

/**
 * Dummy RDP5 channel for testing purposes
 *
 * @author Tom Elliott
 */
public class TestChannel extends VChannel {

	public TestChannel(Options options, String name, int flags) {
		super(options);
		this.name = name;
		this.flags = flags;
	}

	private String name;

	private int flags;

	@Override
	public String name() {
		return name;
	}

	@Override
	public int flags() {
		return flags;
	}

	@Override
	public void process(RdpPacket data) {
	}

}

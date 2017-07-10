/* VChannel.java
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

import java.io.IOException;

import net.propero.rdp.Options;
import net.propero.rdp.RdesktopException;
import net.propero.rdp.RdpPacket;
import net.propero.rdp.Secure;

/**
 * Abstract class for RDP5 channels
 */
public abstract class VChannel {

	private int mcs_id = 0;

	protected final Options options;
	protected Secure secure;
	public VChannel(Options options) {
		this.options = options;
	}

	public void setSecure(Secure secure) {
		this.secure = secure;
	}

	/**
	 * Provide the name of this channel
	 *
	 * @return Channel name as string
	 */
	public abstract String name();

	/**
	 * Provide the set of flags specifying working options for this channel
	 *
	 * @return Option flags
	 */
	public abstract int flags();

	/**
	 * Process a packet sent on this channel
	 *
	 * @param data
	 *            Packet sent to this channel
	 * @throws RdesktopException
	 * @throws IOException
	 */
	public abstract void process(RdpPacket data) throws RdesktopException, IOException;

	public int mcs_id() {
		return mcs_id;
	}

	/**
	 * Set the MCS ID for this channel
	 *
	 * @param mcs_id
	 *            New MCS ID
	 */
	public void set_mcs_id(int mcs_id) {
		this.mcs_id = mcs_id;
	}

	/**
	 * Initialise a packet for transmission over this virtual channel
	 *
	 * @param length
	 *            Desired length of packet
	 * @return Packet prepared for this channel
	 * @throws RdesktopException
	 */
	public RdpPacket init(int length) throws RdesktopException {
		RdpPacket s;

		s = secure.init(Secure.SEC_ENCRYPT, length + 8);
		s.setHeader(RdpPacket.CHANNEL_HEADER);
		s.incrementPosition(8);

		return s;
	}

	/**
	 * Send a packet over this virtual channel
	 *
	 * @param data
	 *            Packet to be sent
	 * @throws RdesktopException
	 * @throws IOException
	 */
	public void send_packet(RdpPacket data) throws RdesktopException, IOException {
		if (secure == null) {
			return;
		}
		synchronized (this.secure) {
			int length = data.size();

			int data_offset = 0;
			int num_packets = (length / VChannels.CHANNEL_CHUNK_LENGTH);
			num_packets += length - (VChannels.CHANNEL_CHUNK_LENGTH) * num_packets;

			while (data_offset < length) {

				int thisLength = Math.min(VChannels.CHANNEL_CHUNK_LENGTH, length
						- data_offset);

				RdpPacket s = secure.init(Secure.SEC_ENCRYPT, 8 + thisLength);
				s.setLittleEndian32(length);

				int flags = ((data_offset == 0) ? VChannels.CHANNEL_FLAG_FIRST : 0);
				if (data_offset + thisLength >= length) {
					flags |= VChannels.CHANNEL_FLAG_LAST;
				}

				if ((this.flags() & VChannels.CHANNEL_OPTION_SHOW_PROTOCOL) != 0) {
					flags |= VChannels.CHANNEL_FLAG_SHOW_PROTOCOL;
				}

				s.setLittleEndian32(flags);
				s.copyFromPacket(data, data_offset, s.getPosition(), thisLength);
				s.incrementPosition(thisLength);
				s.markEnd();

				data_offset += thisLength;

				if (secure != null) {
					secure.send_to_channel(s, Secure.SEC_ENCRYPT, this.mcs_id());
				}
			}
		}
	}

}

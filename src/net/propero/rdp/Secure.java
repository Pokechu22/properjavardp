/* Secure.java
 * Component: ProperJavaRDP
 *
 * Copyright (c) 2005 Propero Limited
 * Copyright (c) 2008 IsmAvatar <cmagicj@nni.com>
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

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import net.propero.rdp.rdp5.Rdp5;
import net.propero.rdp.rdp5.VChannels;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.engines.RC4Engine;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * Secure layer of communication
 */
public class Secure {
	boolean readCert = false;

	private static final Logger LOGGER = LogManager.getLogger();

	private Licence licence;

	/* constants for the secure layer */
	public static final int SEC_ENCRYPT = 0x0008;

	public static final int SEC_LOGON_INFO = 0x0040;

	static final int SEC_RANDOM_SIZE = 32;

	static final int SEC_MODULUS_SIZE = 64;

	static final int SEC_MAX_MODULUS_SIZE = 256;

	static final int SEC_PADDING_SIZE = 8;

	private static final int SEC_EXPONENT_SIZE = 4;

	private static final int SEC_CLIENT_RANDOM = 0x0001;

	static final int SEC_LICENCE_NEG = 0x0080;

	private static final int SEC_TAG_SRV_INFO = 0x0c01;

	private static final int SEC_TAG_SRV_CRYPT = 0x0c02;

	private static final int SEC_TAG_SRV_3 = 0x0c03;

	private static final int SEC_TAG_SRV_CHANNELS = 0x0c03;

	private static final int SEC_TAG_CLI_INFO = 0xc001;

	private static final int SEC_TAG_CLI_CRYPT = 0xc002;

	private static final int SEC_TAG_CLI_CHANNELS = 0xc003;

	private static final int SEC_TAG_CLI_4 = 0xc004;

	private static final int SEC_TAG_PUBKEY = 0x0006;

	private static final int SEC_TAG_KEYSIG = 0x0008;

	private static final int SEC_RSA_MAGIC = 0x31415352; /* RSA1 */

	protected final MCS McsLayer;

	// private String hostname=null;
	// private String username=null;
	boolean licenceIssued = false;

	private final RC4Engine rc4_enc;

	private final RC4Engine rc4_dec;

	private final RC4Engine rc4_update;

	private final SHA1Digest sha1;

	private final MD5Digest md5;

	private int keylength = 0;

	private int enc_count = 0;

	private int dec_count = 0;

	private int server_public_key_len = 0;

	private byte[] sec_sign_key = null;

	private byte[] sec_decrypt_key = null;

	private byte[] sec_encrypt_key = null;

	private byte[] sec_decrypt_update_key = null;

	private byte[] sec_encrypt_update_key = null;

	private byte[] sec_crypted_random = null;

	private byte[] exponent = null;

	private byte[] modulus = null;

	private byte[] server_random = null;

	private byte[] client_random = new byte[SEC_RANDOM_SIZE];

	private static final byte[] pad_54 = { 54, 54, 54, 54, 54, 54, 54, 54, 54,
		54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54,
		54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54 };

	private static final byte[] pad_92 = { 92, 92, 92, 92, 92, 92, 92, 92, 92,
		92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92,
		92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92,
		92, 92, 92, 92, 92 };

	private VChannels channels;

	private final Options options;

	/**
	 * Initialise Secure layer of communications
	 *
	 * @param channels
	 *            Virtual channels for this connection
	 * @param options
	 *            The options instance
	 */
	public Secure(VChannels channels, Options options, Rdp5 rdp) {
		this.channels = channels;
		this.options = options;
		this.licence = new Licence(options, this);
		McsLayer = new MCS(options, channels, this, rdp);
		rc4_dec = new RC4Engine();
		rc4_enc = new RC4Engine();
		rc4_update = new RC4Engine();
		sha1 = new SHA1Digest();
		md5 = new MD5Digest();
		sec_sign_key = new byte[16]; // changed from 8 - rdesktop 1.2.0
		sec_decrypt_key = new byte[16];
		sec_encrypt_key = new byte[16];
		sec_decrypt_update_key = new byte[16]; // changed from 8 - rdesktop
		// 1.2.0
		sec_encrypt_update_key = new byte[16]; // changed from 8 - rdesktop
		// 1.2.0
		sec_crypted_random = new byte[64];

	}

	/**
	 * Connect to server
	 *
	 * @param host
	 *            Address of server to connect to
	 * @param port
	 *            Port to connect to
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws RdesktopException
	 * @throws SocketException
	 * @throws OrderException
	 */
	public void connect(InetAddress host, int port)
			throws UnknownHostException, IOException, RdesktopException,
			SocketException, OrderException {
		if (options.hostname == "") {
			InetAddress localhost = InetAddress.getLocalHost();
			String name = localhost.getHostName();
			StringTokenizer tok = new StringTokenizer(name, ".");
			options.hostname = tok.nextToken();
			options.hostname.trim();
		}

		RdpPacket mcs_data = this.sendMcsData();
		McsLayer.connect(host, port, mcs_data);

		this.processMcsData(mcs_data);

		this.establishKey();
	}

	/**
	 * Connect to server on default port
	 *
	 * @param host
	 *            Server to connect to
	 * @throws IOException
	 * @throws RdesktopException
	 * @throws OrderException
	 */
	public void connect(InetAddress host) throws IOException,
	RdesktopException, OrderException {
		this.connect(host, options.port);
	}

	/**
	 * Close connection
	 */
	public void disconnect() {
		McsLayer.disconnect();
	}

	/**
	 * Construct MCS data, including channel, encryption and display options
	 *
	 * @return Packet populated with MCS data
	 */
	public RdpPacket sendMcsData() {
		LOGGER.debug("Secure.sendMcsData");

		RdpPacket buffer = new RdpPacket(512);

		int hostlen = 2 * (options.hostname == null ? 0 : options.hostname
				.length());

		if (hostlen > 30) {
			hostlen = 30;
		}

		int length = 158;
		if (options.use_rdp5) {
			length += 76 + 12 + 4;
		}

		if (options.use_rdp5 && (channels.num_channels() > 0)) {
			length += channels.num_channels() * 12 + 8;
		}

		buffer.setBigEndian16(5); /* unknown */
		buffer.setBigEndian16(0x14);
		buffer.set8(0x7c);
		buffer.setBigEndian16(1);

		buffer.setBigEndian16(length | 0x8000); // remaining length

		buffer.setBigEndian16(8); // length?
		buffer.setBigEndian16(16);
		buffer.set8(0);
		buffer.setLittleEndian16(0xc001);
		buffer.set8(0);

		buffer.setLittleEndian32(0x61637544); // "Duca" ?!
		buffer.setBigEndian16(length - 14 | 0x8000); // remaining length

		// Client information
		buffer.setLittleEndian16(SEC_TAG_CLI_INFO);
		buffer.setLittleEndian16(options.use_rdp5 ? 212 : 136); // length
		buffer.setLittleEndian16(options.use_rdp5 ? 4 : 1);
		buffer.setLittleEndian16(8);
		buffer.setLittleEndian16(options.width);
		buffer.setLittleEndian16(options.height);
		buffer.setLittleEndian16(0xca01);
		buffer.setLittleEndian16(0xaa03);
		buffer.setLittleEndian32(options.keylayout);
		buffer.setLittleEndian32(options.use_rdp5 ? 2600 : 419); // or 0ece
		// // client
		// build? we
		// are 2600
		// compatible
		// :-)

		/* Unicode name of client, padded to 32 bytes */
		buffer.outUnicodeString(options.hostname.toUpperCase(), hostlen);
		buffer.incrementPosition(30 - hostlen);

		buffer.setLittleEndian32(4);
		buffer.setLittleEndian32(0);
		buffer.setLittleEndian32(12);
		buffer.incrementPosition(64); /* reserved? 4 + 12 doublewords */

		buffer.setLittleEndian16(0xca01); // out_uint16_le(s, 0xca01);
		buffer.setLittleEndian16(options.use_rdp5 ? 1 : 0);

		if (options.use_rdp5) {
			buffer.setLittleEndian32(0); // out_uint32(s, 0);
			buffer.set8(options.server_bpp); // out_uint8(s, g_server_bpp);
			buffer.setLittleEndian16(0x0700); // out_uint16_le(s, 0x0700);
			buffer.set8(0); // out_uint8(s, 0);
			buffer.setLittleEndian32(1); // out_uint32_le(s, 1);

			buffer.incrementPosition(64);

			buffer.setLittleEndian16(SEC_TAG_CLI_4); // out_uint16_le(s,
			// SEC_TAG_CLI_4);
			buffer.setLittleEndian16(12); // out_uint16_le(s, 12);
			buffer.setLittleEndian32(options.console_session ? 0xb : 0xd); // out_uint32_le(s,
			// g_console_session
			// ?
			// 0xb
			// :
			// 9);
			buffer.setLittleEndian32(0); // out_uint32(s, 0);
		}

		// Client encryption settings //
		buffer.setLittleEndian16(SEC_TAG_CLI_CRYPT);
		buffer.setLittleEndian16(options.use_rdp5 ? 12 : 8); // length

		// if(options.use_rdp5) buffer.setLittleEndian32(options.encryption ?
		// 0x1b : 0); // 128-bit encryption supported
		// else
		buffer.setLittleEndian32(options.console_session ? 0xb : 0x3);

		if (options.use_rdp5)
		{
			buffer.setLittleEndian32(0); // unknown
		}

		if (options.use_rdp5 && (channels.num_channels() > 0)) {
			LOGGER.debug(("num_channels is " + channels.num_channels()));
			buffer.setLittleEndian16(SEC_TAG_CLI_CHANNELS); // out_uint16_le(s,
			// SEC_TAG_CLI_CHANNELS);
			buffer.setLittleEndian16(channels.num_channels() * 12 + 8); // out_uint16_le(s,
			// g_num_channels
			// * 12
			// + 8);
			// //
			// length
			buffer.setLittleEndian32(channels.num_channels()); // out_uint32_le(s,
			// g_num_channels);
			// // number of
			// virtual
			// channels
			for (int i = 0; i < channels.num_channels(); i++) {
				LOGGER.debug(("Requesting channel " + channels.channel(i)
						.name()));
				buffer.out_uint8p(channels.channel(i).name(), 8); // out_uint8a(s,
				// g_channels[i].name,
				// 8);
				buffer.setBigEndian32(channels.channel(i).flags()); // out_uint32_be(s,
				// g_channels[i].flags);
			}
		}

		buffer.markEnd();
		return buffer;
	}

	/**
	 * Handle MCS info from server (server info, encryption info and channel
	 * information)
	 *
	 * @param mcs_data
	 *            Data received from server
	 */
	public void processMcsData(RdpPacket mcs_data)
			throws RdesktopException {
		LOGGER.debug("Secure.processMcsData");
		int tag = 0, len = 0, length = 0, nexttag = 0;

		mcs_data.incrementPosition(21); // header (T.124 stuff, probably)
		len = mcs_data.get8();

		if ((len & 0x00000080) != 0) {
			len = mcs_data.get8();
		}

		while (mcs_data.getPosition() < mcs_data.getEnd()) {
			tag = mcs_data.getLittleEndian16();
			length = mcs_data.getLittleEndian16();

			if (length <= 4) {
				return;
			}

			nexttag = mcs_data.getPosition() + length - 4;

			switch (tag) {
			case (Secure.SEC_TAG_SRV_INFO):
				processSrvInfo(mcs_data);
			break;
			case (Secure.SEC_TAG_SRV_CRYPT):
				this.processCryptInfo(mcs_data);
			break;
			case (Secure.SEC_TAG_SRV_CHANNELS):
				/*
				 * FIXME: We should parse this information and use it to map
				 * RDP5 channels to MCS channels
				 */
				break;

			default:
				throw new RdesktopException("Not implemented! Tag:" + tag
						+ "not recognized!");
			}

			mcs_data.setPosition(nexttag);
		}
	}

	/**
	 * Read server info from packet, specifically the RDP version of the server
	 *
	 * @param mcs_data
	 *            Packet to read
	 */
	private void processSrvInfo(RdpPacket mcs_data) {
		options.server_rdp_version = mcs_data.getLittleEndian16(); // in_uint16_le(s,
		// g_server_rdp_version);
		LOGGER.debug(("Server RDP version is " + options.server_rdp_version));
		if (1 == options.server_rdp_version) {
			options.use_rdp5 = false;
		}
	}

	public void establishKey() throws RdesktopException, IOException {
		int length = server_public_key_len + SEC_PADDING_SIZE;
		int flags = SEC_CLIENT_RANDOM;
		RdpPacket buffer = this.init(flags, length + 4);

		buffer.setLittleEndian32(length);

		buffer.copyFromByteArray(this.sec_crypted_random, 0, buffer
				.getPosition(), server_public_key_len);
		buffer.incrementPosition(server_public_key_len);
		buffer.incrementPosition(SEC_PADDING_SIZE);
		buffer.markEnd();
		this.send(buffer, flags);

	}

	public void processCryptInfo(RdpPacket data)
			throws RdesktopException {
		int rc4_key_size = 0;

		rc4_key_size = this.parseCryptInfo(data);
		if (rc4_key_size == 0) {
			return;
		}

		// this.client_random = this.generateRandom(SEC_RANDOM_SIZE);
		LOGGER.debug("readCert = " + readCert);
		if (readCert) { /*
		 * Which means we should use RDP5-style encryption
		 */

			// *** reverse the client random
			// this.reverse(this.client_random);

			// *** load the server public key into the stored data for
			// encryption
			/*
			 * this.exponent =
			 * this.server_public_key.getPublicExponent().toByteArray();
			 * this.modulus = this.server_public_key.getModulus().toByteArray();
			 *
			 * System.out.println("Exponent: " +
			 * server_public_key.getPublicExponent());
			 * System.out.println("Modulus: " + server_public_key.getModulus());
			 */

			// *** perform encryption
			// this.sec_crypted_random = RSA_public_encrypt(this.client_random,
			// this.server_public_key);
			// this.RSAEncrypt(SEC_RANDOM_SIZE);
			// this.RSAEncrypt(SEC_RANDOM_SIZE);
			// *** reverse the random data back
			// this.reverse(this.sec_crypted_random);
		} else {
			this.generateRandom();
			this.RSAEncrypt(SEC_RANDOM_SIZE, server_public_key_len);
		}
		this.generate_keys(rc4_key_size);
	}

	/**
	 * Intialise a packet at the Secure layer
	 *
	 * @param flags
	 *            Encryption flags
	 * @param length
	 *            Length of packet
	 * @return Intialised packet
	 * @throws RdesktopException
	 */
	public RdpPacket init(int flags, int length)
			throws RdesktopException {
		int headerlength = 0;
		RdpPacket buffer;

		if (!this.licenceIssued) {
			headerlength = ((flags & SEC_ENCRYPT) != 0) ? 12 : 4;
		} else {
			headerlength = ((flags & SEC_ENCRYPT) != 0) ? 12 : 0;
		}

		buffer = McsLayer.init(length + headerlength);
		buffer.pushLayer(RdpPacket.SECURE_HEADER, headerlength);
		// buffer.setHeader(RdpPacket_Localised.SECURE_HEADER);
		// buffer.incrementPosition(headerlength);
		// buffer.setStart(buffer.getPosition());
		return buffer;
	}

	/**
	 * Send secure data on the global channel
	 *
	 * @param sec_data
	 *            Data to send
	 * @param flags
	 *            Encryption flags
	 * @throws RdesktopException
	 * @throws IOException
	 */
	public void send(RdpPacket sec_data, int flags)
			throws RdesktopException, IOException {
		send_to_channel(sec_data, flags, MCS.MCS_GLOBAL_CHANNEL);
	}

	/**
	 * Prepare data as a Secure PDU and pass down to the MCS layer
	 *
	 * @param sec_data
	 *            Data to send
	 * @param flags
	 *            Encryption flags
	 * @param channel
	 *            Channel over which to send data
	 * @throws RdesktopException
	 * @throws IOException
	 */
	public void send_to_channel(RdpPacket sec_data, int flags,
			int channel) throws RdesktopException, IOException {
		int datalength = 0;
		byte[] signature = null;
		byte[] data;
		byte[] buffer;

		sec_data.setPosition(sec_data.getHeader(RdpPacket.SECURE_HEADER));

		if (this.licenceIssued == false || (flags & SEC_ENCRYPT) != 0) {
			sec_data.setLittleEndian32(flags);
		}
		if ((flags & SEC_ENCRYPT) != 0) {
			flags &= ~SEC_ENCRYPT;
			datalength = sec_data.getEnd() - sec_data.getPosition() - 8;
			data = new byte[datalength];
			buffer = null;
			sec_data.copyToByteArray(data, 0, sec_data.getPosition() + 8,
					datalength);
			signature = this.sign(this.sec_sign_key, 8, this.keylength, data,
					datalength);

			buffer = this.encrypt(data, datalength);

			sec_data.copyFromByteArray(signature, 0, sec_data.getPosition(), 8);
			sec_data.copyFromByteArray(buffer, 0, sec_data.getPosition() + 8,
					datalength);

		}
		// McsLayer.send(sec_data);
		McsLayer.send_to_channel(sec_data, channel);
	}

	/**
	 * Generate MD5 signature
	 *
	 * @param session_key
	 *            Key with which to sign data
	 * @param length
	 *            Length of signature
	 * @param keylen
	 *            Length of key
	 * @param data
	 *            Data to sign
	 * @param datalength
	 *            Length of data to sign
	 * @return Signature for data
	 */
	public byte[] sign(byte[] session_key, int length, int keylen, byte[] data,
			int datalength) {
		byte[] shasig = new byte[20];
		byte[] md5sig = new byte[16];
		byte[] lenhdr = new byte[4];
		byte[] signature = new byte[length];

		this.setLittleEndian32(lenhdr, datalength);

		sha1.reset();
		sha1.update(session_key, 0, keylen/* length */);
		sha1.update(pad_54, 0, 40);
		sha1.update(lenhdr, 0, 4);
		sha1.update(data, 0, datalength);
		sha1.doFinal(shasig, 0);
		sha1.reset();

		md5.reset();
		md5.update(session_key, 0, keylen/* length */);
		md5.update(pad_92, 0, 48);
		md5.update(shasig, 0, 20);
		md5.doFinal(md5sig, 0);
		md5.reset();

		System.arraycopy(md5sig, 0, signature, 0, length);
		return signature;
	}

	/**
	 * Encrypt specified number of bytes from provided data using RC4 algorithm
	 *
	 * @param data
	 *            Data to encrypt
	 * @param length
	 *            Number of bytes to encrypt (from start of array)
	 * @return Encrypted data
	 */
	public byte[] encrypt(byte[] data, int length) {
		byte[] buffer = new byte[length];
		if (this.enc_count == 4096) {
			sec_encrypt_key = this.update(this.sec_encrypt_key,
					this.sec_encrypt_update_key);
			byte[] key = new byte[this.keylength];
			System.arraycopy(this.sec_encrypt_key, 0, key, 0, this.keylength);
			this.rc4_enc.init(true, new KeyParameter(key));
			// logger.debug("Packet enc_count="+enc_count);
			this.enc_count = 0;
		}
		// this.rc4.engineInitEncrypt(this.rc4_encrypt_key);
		this.rc4_enc.processBytes(data, 0, length, buffer, 0);
		this.enc_count++;
		return buffer;
	}

	/**
	 * Encrypt provided data using the RC4 algorithm
	 *
	 * @param data
	 *            Data to encrypt
	 * @return Encrypted data
	 */
	public byte[] encrypt(byte[] data) {
		byte[] buffer = new byte[data.length];
		if (this.enc_count == 4096) {
			sec_encrypt_key = this.update(this.sec_encrypt_key,
					this.sec_encrypt_update_key);
			byte[] key = new byte[this.keylength];
			System.arraycopy(this.sec_encrypt_key, 0, key, 0, this.keylength);
			this.rc4_enc.init(true, new KeyParameter(key));
			// logger.debug("Packet enc_count="+enc_count);
			this.enc_count = 0;
		}
		// this.rc4.engineInitEncrypt(this.rc4_encrypt_key);

		this.rc4_enc.processBytes(data, 0, data.length, buffer, 0);
		this.enc_count++;
		return buffer;
	}

	/**
	 * Decrypt specified number of bytes from provided data using RC4 algorithm
	 *
	 * @param data
	 *            Data to decrypt
	 * @param length
	 *            Number of bytes to decrypt (from start of array)
	 * @return Decrypted data
	 */
	public byte[] decrypt(byte[] data, int length) {
		byte[] buffer = new byte[length];
		if (this.dec_count == 4096) {
			sec_decrypt_key = this.update(this.sec_decrypt_key,
					this.sec_decrypt_update_key);
			byte[] key = new byte[this.keylength];
			System.arraycopy(this.sec_decrypt_key, 0, key, 0, this.keylength);
			this.rc4_dec.init(false, new KeyParameter(key));
			// logger.debug("Packet dec_count="+dec_count);
			this.dec_count = 0;
		}
		// this.rc4.engineInitDecrypt(this.rc4_decrypt_key);
		this.rc4_dec.processBytes(data, 0, length, buffer, 0);
		this.dec_count++;
		return buffer;
	}

	/**
	 * Decrypt provided data using RC4 algorithm
	 *
	 * @param data
	 *            Data to decrypt
	 * @return Decrypted data
	 */
	public byte[] decrypt(byte[] data) {
		byte[] buffer = new byte[data.length];
		if (this.dec_count == 4096) {
			sec_decrypt_key = this.update(this.sec_decrypt_key,
					this.sec_decrypt_update_key);
			byte[] key = new byte[this.keylength];
			System.arraycopy(this.sec_decrypt_key, 0, key, 0, this.keylength);
			this.rc4_dec.init(false, new KeyParameter(key));
			// logger.debug("Packet dec_count="+dec_count);
			this.dec_count = 0;
		}
		// this.rc4.engineInitDecrypt(this.rc4_decrypt_key);

		this.rc4_dec.processBytes(data, 0, data.length, buffer, 0);
		this.dec_count++;
		return buffer;
	}

	/**
	 * Read encryption information from a Secure layer PDU, obtaining and
	 * storing level of encryption and any keys received
	 *
	 * @param data
	 *            Packet to read encryption information from
	 * @return Size of RC4 key
	 * @throws RdesktopException
	 */
	public int parseCryptInfo(RdpPacket data)
			throws RdesktopException {
		LOGGER.debug("Secure.parseCryptInfo");
		int encryption_level = 0, random_length = 0, RSA_info_length = 0;
		int tag = 0, length = 0;
		int next_tag = 0, end = 0;
		int rc4_key_size = 0;

		rc4_key_size = data.getLittleEndian32(); // 1 = 40-Bit 2 = 128 Bit
		encryption_level = data.getLittleEndian32(); // 1 = low, 2 = medium,
		// 3 = high
		if (encryption_level == 0) { // no encryption
			return 0;
		}
		random_length = data.getLittleEndian32();
		RSA_info_length = data.getLittleEndian32();

		if (random_length != SEC_RANDOM_SIZE) {
			throw new RdesktopException("Wrong Size of Random! Got"
					+ random_length + "expected" + SEC_RANDOM_SIZE);
		}
		this.server_random = new byte[random_length];
		data.copyToByteArray(this.server_random, 0, data.getPosition(),
				random_length);
		data.incrementPosition(random_length);

		end = data.getPosition() + RSA_info_length;

		if (end > data.getEnd()) {
			LOGGER.debug("Reached end of crypt info prematurely ");
			return 0;
		}

		// data.incrementPosition(12); // unknown bytes
		int flags = data.getLittleEndian32(); // in_uint32_le(s, flags); // 1
		// = RDP4-style, 0x80000002 =
		// X.509
		LOGGER.debug("Flags = 0x" + Integer.toHexString(flags));
		if ((flags & 1) != 0) {
			LOGGER.debug(("We're going for the RDP4-style encryption"));
			data.incrementPosition(8); // in_uint8s(s, 8); // unknown

			while (data.getPosition() < data.getEnd()) {
				tag = data.getLittleEndian16();
				length = data.getLittleEndian16();

				next_tag = data.getPosition() + length;

				switch (tag) {

				case (Secure.SEC_TAG_PUBKEY):

					if (!parsePublicKey(data)) {
						return 0;
					}

				break;
				case (Secure.SEC_TAG_KEYSIG):
					// Microsoft issued a key but we don't care
					break;

				default:
					throw new RdesktopException("Unimplemented decrypt tag "
							+ tag);
				}
				data.setPosition(next_tag);
			}

			if (data.getPosition() == data.getEnd()) {
				return rc4_key_size;
			} else {
				LOGGER.warn("End not reached!");
				return 0;
			}

		} else {
			data.getLittleEndian32(); // number of certificates

			int cacert_len = data.getLittleEndian32();
			data.incrementPosition(cacert_len);
			int cert_len = data.getLittleEndian32();
			data.incrementPosition(cert_len);

			readCert = true;

			return rc4_key_size;
		}

	}

	/*
	 * public X509Certificate readCert(int length, RdpPacket_Localised data){
	 * byte[] buf = new byte[length];
	 *
	 * data.copyToByteArray(buf,0,data.getPosition(),buf.length);
	 * data.incrementPosition(length);
	 *
	 * for(int i = 0; i < buf.length; i++){ buf[i] = (byte) (buf[i] & 0xFF); }
	 *
	 * ByteArrayInputStream bIn = new ByteArrayInputStream(buf); X509Certificate
	 * cert = null; CertificateFactory cf = null; try { cf =
	 * CertificateFactory.getInstance("X.509"); cert =
	 * (X509Certificate)cf.generateCertificate(bIn); } catch
	 * (CertificateException e) { // TODO Auto-generated catch block
	 * e.printStackTrace(); }
	 *
	 * bIn.reset(); return cert; }
	 */
	public void generateRandom() {
		/*
		 * try{ SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
		 * random.nextBytes(this.client_random); }
		 * catch(NoSuchAlgorithmException e){logger.warn("No Such Random
		 * Algorithm");}
		 */
	}

	public void RSAEncrypt(int length, int modulus_size)
			throws RdesktopException {
		byte[] inr = new byte[length];
		// int outlength = 0;
		BigInteger mod = null;
		BigInteger exp = null;
		BigInteger x = null;

		this.reverse(this.exponent);
		this.reverse(this.modulus);
		System.arraycopy(this.client_random, 0, inr, 0, length);
		this.reverse(inr);

		if ((this.modulus[0] & 0x80) != 0) {
			byte[] temp = new byte[this.modulus.length + 1];
			System.arraycopy(this.modulus, 0, temp, 1, this.modulus.length);
			temp[0] = 0;
			mod = new BigInteger(temp);
		} else {
			mod = new BigInteger(this.modulus);
		}
		if ((this.exponent[0] & 0x80) != 0) {
			byte[] temp = new byte[this.exponent.length + 1];
			System.arraycopy(this.exponent, 0, temp, 1, this.exponent.length);
			temp[0] = 0;
			exp = new BigInteger(temp);
		} else {
			exp = new BigInteger(this.exponent);
		}
		if ((inr[0] & 0x80) != 0) {
			byte[] temp = new byte[inr.length + 1];
			System.arraycopy(inr, 0, temp, 1, inr.length);
			temp[0] = 0;
			x = new BigInteger(temp);
		} else {
			x = new BigInteger(inr);
		}

		BigInteger y = x.modPow(exp, mod);
		this.sec_crypted_random = y.toByteArray();

		if ((this.sec_crypted_random[0] & 0x80) != 0) {
			throw new RdesktopException(
					"Wrong Sign! Expected positive Integer!");
		}

		if (this.sec_crypted_random.length > SEC_MAX_MODULUS_SIZE) {
			LOGGER.warn("sec_crypted_random too big!"); /* FIXME */
		}
		this.reverse(this.sec_crypted_random);

		byte[] temp = new byte[SEC_MAX_MODULUS_SIZE];

		if (this.sec_crypted_random.length < modulus_size) {
			System.arraycopy(this.sec_crypted_random, 0, temp, 0,
					this.sec_crypted_random.length);
			for (int i = this.sec_crypted_random.length; i < temp.length; i++) {
				temp[i] = 0;
			}
			this.sec_crypted_random = temp;

		}

	}

	/**
	 * Read in a public key from a provided Secure layer PDU, and store in
	 * this.exponent and this.modulus
	 *
	 * @param data
	 *            Secure layer PDU containing key data
	 * @return True if key successfully read
	 * @throws RdesktopException
	 */
	public boolean parsePublicKey(RdpPacket data)
			throws RdesktopException {
		int magic = 0, modulus_length = 0;

		magic = data.getLittleEndian32();

		if (magic != SEC_RSA_MAGIC) {
			throw new RdesktopException("Wrong magic! Expected" + SEC_RSA_MAGIC
					+ "got:" + magic);
		}

		modulus_length = data.getLittleEndian32() - SEC_PADDING_SIZE;

		if (modulus_length < 64 || modulus_length > SEC_MAX_MODULUS_SIZE) {
			throw new RdesktopException("Bad server public key size ("
					+ (modulus_length * 8) + " bites)");
		}

		data.incrementPosition(8); // unknown modulus bits
		this.exponent = new byte[SEC_EXPONENT_SIZE];
		data.copyToByteArray(this.exponent, 0, data.getPosition(),
				SEC_EXPONENT_SIZE);
		data.incrementPosition(SEC_EXPONENT_SIZE);
		this.modulus = new byte[modulus_length];
		data.copyToByteArray(this.modulus, 0, data.getPosition(),
				modulus_length);
		data.incrementPosition(modulus_length);
		data.incrementPosition(SEC_PADDING_SIZE);
		this.server_public_key_len = modulus_length;

		if (data.getPosition() <= data.getEnd()) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Reverse the values in the provided array
	 *
	 * @param data
	 *            Array as passed reversed on return
	 */
	public void reverse(byte[] data) {
		int i = 0, j = 0;
		byte temp = 0;

		for (i = 0, j = data.length - 1; i < j; i++, j--) {
			temp = data[i];
			data[i] = data[j];
			data[j] = temp;
		}
	}

	public void reverse(byte[] data, int length) {
		int i = 0, j = 0;
		byte temp = 0;

		for (i = 0, j = length - 1; i < j; i++, j--) {
			temp = data[i];
			data[i] = data[j];
			data[j] = temp;
		}
	}

	public byte[] hash48(byte[] in, byte[] salt1, byte[] salt2, int salt) {
		byte[] shasig = new byte[20];
		byte[] pad = new byte[4];
		byte[] out = new byte[48];
		int i = 0;

		for (i = 0; i < 3; i++) {
			for (int j = 0; j <= i; j++) {
				pad[j] = (byte) (salt + i);
			}
			sha1.update(pad, 0, i + 1);
			sha1.update(in, 0, 48);
			sha1.update(salt1, 0, 32);
			sha1.update(salt2, 0, 32);
			sha1.doFinal(shasig, 0);
			sha1.reset();

			md5.update(in, 0, 48);
			md5.update(shasig, 0, 20);
			md5.doFinal(out, i * 16); // TODO: make sure this doesn't clobber anything
		}

		return out;
	}

	public byte[] hash16(byte[] in, byte[] salt1, byte[] salt2, int in_position) {

		md5.reset();
		md5.update(in, in_position, 16);
		md5.update(salt1, 0, 32);
		md5.update(salt2, 0, 32);
		byte[] result = new byte[16];
		md5.doFinal(result, 0);
		return result;
	}

	/**
	 * Generate a 40-bit key and store in the parameter key.
	 *
	 * @param key
	 */
	public void make40bit(byte[] key) {
		key[0] = (byte) 0xd1;
		key[1] = (byte) 0x26;
		key[2] = (byte) 0x9e;
	}

	/**
	 *
	 * @param key
	 * @param update_key
	 * @return
	 */
	public byte[] update(byte[] key, byte[] update_key) {
		byte[] shasig = new byte[20];
		byte[] update = new byte[this.keylength]; // changed from 8 - rdesktop
		// 1.2.0
		byte[] thekey = new byte[key.length];

		sha1.reset();
		sha1.update(update_key, 0, keylength);
		sha1.update(pad_54, 0, 40);
		sha1.update(key, 0, keylength); // changed from 8 - rdesktop
		// 1.2.0
		sha1.doFinal(shasig, 0);
		sha1.reset();

		md5.reset();
		md5.update(update_key, 0, keylength); // changed from 8 - rdesktop
		// 1.2.0
		md5.update(pad_92, 0, 48);
		md5.update(shasig, 0, 20);
		md5.doFinal(thekey, 0);
		md5.reset();

		System.arraycopy(thekey, 0, update, 0, this.keylength);
		rc4_update.init(false, new KeyParameter(update));
		// added
		rc4_update.processBytes(thekey, 0, this.keylength, thekey, 0);

		if (this.keylength == 8) {
			this.make40bit(thekey);
		}

		return thekey;
	}

	/**
	 * Write a 32-bit integer value to an array of bytes, length 4
	 *
	 * @param data
	 *            Modified by method to be a 4-byte array representing the
	 *            parameter value
	 * @param value
	 *            Integer value to return as a little-endian 32-bit value
	 */
	public void setLittleEndian32(byte[] data, int value) {

		data[3] = (byte) ((value >>> 24) & 0xff);
		data[2] = (byte) ((value >>> 16) & 0xff);
		data[1] = (byte) ((value >>> 8) & 0xff);
		data[0] = (byte) (value & 0xff);
	}

	/**
	 * Receive a Secure layer PDU from the MCS layer
	 *
	 * @return Packet representing received Secure PDU
	 * @throws RdesktopException
	 * @throws IOException
	 * @throws OrderException
	 */
	public RdpPacket receive() throws RdesktopException, IOException, OrderException {
		int sec_flags = 0;
		RdpPacket buffer = null;
		while (true) {
			int[] channel = new int[1];
			buffer = McsLayer.receive(channel);
			if (buffer == null) {
				return null;
			}
			buffer.setHeader(RdpPacket.SECURE_HEADER);
			sec_flags = buffer.getLittleEndian32();

			if ((sec_flags & SEC_LICENCE_NEG) != 0) {
				licence.process(buffer);
				continue;
			}
			if ((sec_flags & SEC_ENCRYPT) != 0) {
				buffer.incrementPosition(8); // signature
				byte[] data = new byte[buffer.size() - buffer.getPosition()];
				buffer.copyToByteArray(data, 0, buffer.getPosition(),
						data.length);
				byte[] packet = this.decrypt(data);

				buffer.copyFromByteArray(packet, 0, buffer.getPosition(),
						packet.length);

				// buffer.setStart(buffer.getPosition());
				// return buffer;
			}

			if (channel[0] != MCS.MCS_GLOBAL_CHANNEL) {
				channels.channel_process(buffer, channel[0]);
				continue;
			}

			buffer.setStart(buffer.getPosition());
			return buffer;
		}
	}

	/**
	 * Generate encryption keys of applicable size for connection
	 *
	 * @param rc4_key_size
	 *            Size of keys to generate (1 if 40-bit encryption, otherwise
	 *            128-bit)
	 */
	public void generate_keys(int rc4_key_size) {
		byte[] session_key = new byte[48];
		byte[] temp_hash = new byte[48];
		byte[] input = new byte[48];

		System.arraycopy(this.client_random, 0, input, 0, 24);
		System.arraycopy(this.server_random, 0, input, 24, 24);

		temp_hash = this.hash48(input, this.client_random, this.server_random,
				65);
		session_key = this.hash48(temp_hash, this.client_random,
				this.server_random, 88);

		System.arraycopy(session_key, 0, this.sec_sign_key, 0, 16);
		// changed from 8 - rdesktop 1.2.0

		this.sec_decrypt_key = this.hash16(session_key, this.client_random,
				this.server_random, 16);
		this.sec_encrypt_key = this.hash16(session_key, this.client_random,
				this.server_random, 32);

		if (rc4_key_size == 1) {
			LOGGER.info("40 Bit Encryption enabled");
			this.make40bit(this.sec_sign_key);
			this.make40bit(this.sec_decrypt_key);
			this.make40bit(this.sec_encrypt_key);
			this.keylength = 8;
		} else {
			LOGGER.info("128 Bit Encryption enabled");
			this.keylength = 16;
		}

		System.arraycopy(this.sec_decrypt_key, 0, this.sec_decrypt_update_key,
				0, 16); // changed from 8 - rdesktop 1.2.0
		System.arraycopy(this.sec_encrypt_key, 0, this.sec_encrypt_update_key,
				0, 16); // changed from 8 - rdesktop 1.2.0

		byte[] key = new byte[this.keylength];
		System.arraycopy(this.sec_encrypt_key, 0, key, 0, this.keylength);
		rc4_enc.init(true, new KeyParameter(key));
		System.arraycopy(this.sec_decrypt_key, 0, key, 0, this.keylength);
		rc4_dec.init(false, new KeyParameter(key));
	}

	/**
	 * @return MCS user ID
	 */
	public int getUserID() {
		return McsLayer.getUserID();
	}
}
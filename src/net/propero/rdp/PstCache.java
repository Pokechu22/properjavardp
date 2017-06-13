/* PstCache.java
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

package net.propero.rdp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handle persistent caching
 */
class PstCache {

	protected static Logger logger = LogManager.getLogger(Rdp.class);

	public static final int MAX_CELL_SIZE = 0x1000; /* pixels */

	private final Options options;
	private final Cache cache;

	public PstCache(Options options, Cache cache) {
		this.options = options;
		this.cache = cache;
	}

	public boolean IS_PERSISTENT(int id) {
		return (id < 8 && pstcache_fd[id] != null);
	}

	public int stamp;

	private File[] pstcache_fd = new File[8];

	private int pstcache_Bpp;

	private boolean pstcache_enumerated = false;

	/* Update usage info for a bitmap */
	protected void touchBitmap(int cache_id, int cache_idx, int stamp) {
		logger.info("PstCache.touchBitmap");

		if (!IS_PERSISTENT(cache_id) || cache_idx >= Rdp.BMPCACHE2_NUM_PSTCELLS) {
			return;
		}

		try (FileOutputStream fd = new FileOutputStream(pstcache_fd[cache_id])) {
			fd.write(toBigEndian32(stamp), 12 + cache_idx
					* (pstcache_Bpp * MAX_CELL_SIZE + CELLHEADER.size()), 4);
			// rd_lseek_file(fd, 12 + cache_idx * (g_pstcache_Bpp *
			// MAX_CELL_SIZE + sizeof(CELLHEADER))); // this seems to do nothing
			// (return 0) in rdesktop
			// rd_write_file(fd, &stamp, sizeof(stamp)); // same with this
			// one???

		} catch (IOException e) {
			logger.warn("Failed to touch bitmap (" + cache_id + "/" + cache_idx + "/" + stamp + ")", e);
			return;
		}
	}

	private static byte[] toBigEndian32(int value) {
		byte[] out = new byte[4];
		out[0] = (byte) (value & 0xFF);
		out[1] = (byte) (value & 0xFF00);
		out[2] = (byte) (value & 0xFF0000);
		out[3] = (byte) (value & 0xFF000000);
		return out;
	}

	/* Load a bitmap from the persistent cache */
	public boolean pstcache_load_bitmap(int cache_id, int cache_idx)
			throws IOException, RdesktopException {
		logger.info("PstCache.pstcache_load_bitmap");
		byte[] celldata = null;
		// CELLHEADER cellhdr;
		Bitmap bitmap;
		byte[] cellHead = null;

		if (!options.persistent_bitmap_caching) {
			return false;
		}

		if (!IS_PERSISTENT(cache_id) || cache_idx >= Rdp.BMPCACHE2_NUM_PSTCELLS) {
			return false;
		}

		CELLHEADER c;
		try (FileInputStream fd = new FileInputStream(pstcache_fd[cache_id])) {
			int offset = cache_idx
					* (pstcache_Bpp * MAX_CELL_SIZE + CELLHEADER.size());
			fd.read(cellHead, offset, CELLHEADER.size());
			c = new CELLHEADER(cellHead);
			// rd_lseek_file(fd, cache_idx * (g_pstcache_Bpp * MAX_CELL_SIZE +
			// sizeof(CELLHEADER)));
			// rd_read_file(fd, &cellhdr, sizeof(CELLHEADER));
			// celldata = (uint8 *) xmalloc(cellhdr.length);
			// rd_read_file(fd, celldata, cellhdr.length);
			celldata = new byte[c.length];
			fd.read(celldata);
			logger.debug("Loading bitmap from disk (" + cache_id + ":" + cache_idx
					+ ")\n");
		}

		bitmap = new Bitmap(options, celldata, c.width, c.height, 0, 0, options.Bpp);
		// bitmap = ui_create_bitmap(cellhdr.width, cellhdr.height, celldata);
		this.cache.putBitmap(cache_id, cache_idx, bitmap, c.stamp);

		// xfree(celldata);
		return true;
	}

	/* Store a bitmap in the persistent cache */
	public boolean pstcache_put_bitmap(int cache_id, int cache_idx,
			byte[] bitmap_id, int width, int height, int length, byte[] data)
					throws IOException {
		logger.info("PstCache.pstcache_put_bitmap");
		CELLHEADER cellhdr = new CELLHEADER();

		if (!IS_PERSISTENT(cache_id) || cache_idx >= Rdp.BMPCACHE2_NUM_PSTCELLS) {
			return false;
		}

		cellhdr.bitmap_id = bitmap_id;
		// memcpy(cellhdr.bitmap_id, bitmap_id, 8/* sizeof(BITMAP_ID) */);

		cellhdr.width = width;
		cellhdr.height = height;
		cellhdr.length = length;
		cellhdr.stamp = 0;

		try (FileOutputStream fd = new FileOutputStream(pstcache_fd[cache_id])) {
			int offset = cache_idx
					* (options.Bpp * MAX_CELL_SIZE + CELLHEADER.size());
			fd.write(cellhdr.toBytes(), offset, CELLHEADER.size());
			fd.write(data);
			// rd_lseek_file(fd, cache_idx * (g_pstcache_Bpp * MAX_CELL_SIZE +
			// sizeof(CELLHEADER)));
			// rd_write_file(fd, &cellhdr, sizeof(CELLHEADER));
			// rd_write_file(fd, data, length);
		}
		return true;
	}

	/* list the bitmaps from the persistent cache file */
	public int pstcache_enumerate(int cache_id, int[] idlist)
			throws IOException, RdesktopException {
		logger.info("PstCache.pstcache_enumerate");
		int n, c = 0;
		CELLHEADER cellhdr = null;

		if (!(options.bitmap_caching && options.persistent_bitmap_caching && IS_PERSISTENT(cache_id))) {
			return 0;
		}

		/*
		 * The server disconnects if the bitmap cache content is sent more than
		 * once
		 */
		if (pstcache_enumerated) {
			return 0;
		}

		logger.debug("pstcache enumeration... ");
		for (n = 0; n < Rdp.BMPCACHE2_NUM_PSTCELLS; n++) {
			byte[] cellhead_data = new byte[CELLHEADER.size()];
			try (FileInputStream fd = new FileInputStream(pstcache_fd[cache_id])) {
				if (fd.read(cellhead_data, n
						* (pstcache_Bpp * MAX_CELL_SIZE + CELLHEADER.size()),
						CELLHEADER.size()) <= 0) {
					break;
				}
			}

			cellhdr = new CELLHEADER(cellhead_data);

			int result = 0;
			for (int i = 0; i < cellhdr.bitmap_id.length; i++) {
				result += cellhdr.bitmap_id[i];
			}

			if (result != 0) {
				for (int i = 0; i < 8; i++) {
					idlist[(n * 8) + i] = cellhdr.bitmap_id[i];
				}

				if (cellhdr.stamp != 0) {
					/*
					 * Pre-caching is not possible with 8bpp because a colourmap
					 * is needed to load them
					 */
					if (options.precache_bitmaps && (options.server_bpp > 8)) {
						if (pstcache_load_bitmap(cache_id, n)) {
							c++;
						}
					}

					stamp = Math.max(stamp, cellhdr.stamp);
				}
			} else {
				break;
			}
		}

		logger.info(n + " bitmaps in persistent cache, " + c
				+ " bitmaps loaded in memory\n");
		pstcache_enumerated = true;
		return n;
	}

	/* initialise the persistent bitmap cache */
	public boolean pstcache_init(int cache_id) {
		// int fd;
		String filename;

		if (pstcache_enumerated) {
			return true;
		}

		pstcache_fd[cache_id] = null;

		if (!(options.bitmap_caching && options.persistent_bitmap_caching)) {
			return false;
		}

		pstcache_Bpp = options.Bpp;
		filename = "./cache/pstcache_" + cache_id + "_" + pstcache_Bpp;
		logger.debug("persistent bitmap cache file: " + filename);

		File cacheDir = new File("./cache/");
		if (!cacheDir.exists() && !cacheDir.mkdir()) {
			logger.warn("failed to get/make cache directory");
			return false;
		}

		File f = new File(filename);

		try {
			if (!f.exists() && !f.createNewFile()) {
				logger.warn("Could not create cache file");
				return false;
			}
		} catch (IOException e) {
			logger.warn("Failed to create cache file!", e);
			return false;
		}

		/*
		 * if (!rd_lock_file(fd, 0, 0)) { logger.warn("Persistent bitmap caching
		 * is disabled. (The file is already in use)\n"); rd_close_file(fd);
		 * return false; }
		 */

		pstcache_fd[cache_id] = f;
		return true;
	}

}

/* Header for an entry in the persistent bitmap cache file */
class CELLHEADER {
	byte[] bitmap_id = new byte[8]; // int8 *

	int width, height; // int8

	int length; // int16

	int stamp; // int32

	static int size() {
		return 8 * 8 + 8 * 2 + 16 + 32;
	}

	public CELLHEADER() {

	}

	public CELLHEADER(byte[] data) {
		for (int i = 0; i < bitmap_id.length; i++) {
			bitmap_id[i] = data[i];
		}

		width = data[bitmap_id.length];
		height = data[bitmap_id.length + 1];
		length = (data[bitmap_id.length + 2] >> 8) + data[bitmap_id.length + 3];
		stamp = (data[bitmap_id.length + 6] >> 24)
				+ (data[bitmap_id.length + 6] >> 16)
				+ (data[bitmap_id.length + 6] >> 8)
				+ data[bitmap_id.length + 7];
	}

	public byte[] toBytes() {
		return null;
	}
}

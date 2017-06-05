/* LicenceStore.java
 * Component: ProperJavaRDP
 * 
 * Revision: $Revision$
 * Author: $Author$
 * Date: $Date$
 *
 * Copyright (c) 2005 Propero Limited
 *
 * Purpose: Handle saving and loading of licences
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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.prefs.Preferences;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LicenceStore {
	private LicenceStore() { throw new AssertionError(); }

	private static Logger logger = LogManager.getLogger();

	/**
	 * Load a licence from a file
	 * 
	 * @return Licence data stored in file
	 */
	public static byte[] load_licence(Options options) {
		Preferences prefs = Preferences.userNodeForPackage(LicenceStore.class);
		byte[] data = prefs.getByteArray("licence." + options.hostname, null);

		if (data != null) {
			return data;
		}

		// Try original version in case it didn't load:
		String path = options.licence_path + "/licence." + options.hostname;
		try (FileInputStream fd = new FileInputStream(path)) {
			data = new byte[fd.available()];
			fd.read(data);
		} catch (FileNotFoundException e) {
			logger.warn("Licence file not found!", e);
		} catch (IOException e) {
			logger.warn("IOException in load_licence", e);
		}

		// Well, the old format is present; migrate it by resaving:
		save_licence(options, data);
		return data;
	}

	/**
	 * Save a licence to file
	 * 
	 * @param databytes
	 *            Licence data to store
	 */
	public static void save_licence(Options options, byte[] databytes) {
		Preferences prefs = Preferences.userNodeForPackage(LicenceStore.class);
		prefs.putByteArray("licence." + options.hostname, databytes);

		// Original version for saving, for reference only:
		/* set and create the directory -- if it doesn't exist. */
		// String home = "/root";
//		String dirpath = options.licence_path;// home+"/.rdesktop";
//		String filepath = dirpath + "/licence." + options.hostname;
//
//		File file = new File(dirpath);
//		file.mkdir();
//		try {
//			FileOutputStream fd = new FileOutputStream(filepath);
//
//			/* write to the licence file */
//			fd.write(databytes);
//			fd.close();
//			logger.info("Stored licence at " + filepath);
//		} catch (FileNotFoundException e) {
//			logger.warn("save_licence: file path not valid!", e);
//		} catch (IOException e) {
//			logger.warn("IOException in save_licence", e);
//		}
	}

}

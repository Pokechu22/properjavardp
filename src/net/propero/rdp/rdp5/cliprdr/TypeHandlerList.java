/* TypeHandlerList.java
 * Component: ProperJavaRDP
 *
 * Revision: $Revision$
 * Author: $Author$
 * Date: $Date$
 *
 * Copyright (c) 2005 Propero Limited
 *
 * Purpose:
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
package net.propero.rdp.rdp5.cliprdr;

import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.Iterator;

import net.propero.rdp.RdpPacket;

public class TypeHandlerList implements Iterable<TypeHandler> {

	private ArrayList<TypeHandler> handlers = new ArrayList<TypeHandler>();

	private int count;

	public TypeHandlerList() {
		count = 0;
	}

	public void add(TypeHandler t) {
		if (t != null) {
			handlers.add(t);
			count++;
		}
	}

	public TypeHandler getHandlerForFormat(int format) {
		for (TypeHandler handler : handlers) {
			if ((handler != null) && handler.formatValid(format)) {
				return handler;
			}
		}
		return null;
	}

	public TypeHandlerList getHandlersForMimeType(String mimeType) {
		TypeHandlerList outList = new TypeHandlerList();

		for (TypeHandler handler : handlers) {
			if (handler.mimeTypeValid(mimeType)) {
				outList.add(handler);
			}
		}
		return outList;
	}

	public TypeHandlerList getHandlersForClipboard(DataFlavor[] dataTypes) {
		TypeHandlerList outList = new TypeHandlerList();

		for (TypeHandler handler : handlers) {
			if (handler.clipboardValid(dataTypes)) {
				outList.add(handler);
			}
		}
		return outList;
	}

	public void writeTypeDefinitions(RdpPacket data) {
		for (TypeHandler handler : handlers) {
			data.setLittleEndian32(handler.preferredFormat());
			data.incrementPosition(32);
		}
	}

	public int count() {
		return count;
	}

	/**
	 * Gets the first type handler in the list. If there are no type handlers,
	 * returns null.
	 */
	public TypeHandler getFirst() {
		if (count > 0) {
			return handlers.get(0);
		} else {
			return null;
		}
	}

	@Override
	public Iterator<TypeHandler> iterator() {
		return handlers.iterator();
	}
}

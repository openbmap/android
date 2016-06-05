/*
	Radiobeacon - Openbmap wifi and cell logger
    Copyright (C) 2013  wish7

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.openbmap.db.models;

import org.openbmap.RadioBeacon;

/*
 * Model for ESSID (extended BSSID)
 * @see http://en.wikipedia.org/wiki/Service_set_(802.11_network)
 */

public class EssidRecord implements Comparable<EssidRecord> {
	private String mBssid;
	private int mLength;
	private int mSessionId;

	public EssidRecord(final String bssid) {
		this(bssid, RadioBeacon.SESSION_NOT_TRACKING);
	}

	public EssidRecord(final String bssid, final int session) {
		setBssid(bssid);
		setSessionId(session);
		setLength(bssid.length());
	}

	@Override
	public final int compareTo(final EssidRecord abssid) {
		int res = 0;
		int cou = 0;

		if ((getLength() < 5) && abssid.getLength() < 5) {
			return 0;
		}

		if (getLength() > abssid.getLength()) {
			cou = abssid.getLength();
		} else {
			cou = getLength();
		}

		for (int start = 0; start < cou - 1; start++) {
			res = start;
			int end = start + 1;
			if (!getBssid().substring(start, end).equals(abssid.getBssid().substring(start, end))) {
				break;
			}
		}
		return res;
	}

	public final int getSessionId() {
		return mSessionId;
	}


	public final void setSessionId(final int session) {
		this.mSessionId = session;
	}

	public final String getBssid() {
		return mBssid;
	}

	public final void setBssid(final String bssid) {
		this.mBssid = bssid;
	}

	public final int getLength() {
		return mLength;
	}

	public final void setLength(final int length) {
		this.mLength = length;
	}

}

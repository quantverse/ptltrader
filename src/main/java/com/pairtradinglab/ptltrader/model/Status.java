/**
 * 	This file is part of PTL Trader.
 *
 * 	Copyright © 2011-2021 Quantverse OÜ. All Rights Reserved.
 *
 *  PTL Trader is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  PTL Trader is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with PTL Trader. If not, see <https://www.gnu.org/licenses/>.
 */
package com.pairtradinglab.ptltrader.model;

import net.jcip.annotations.*;

@ThreadSafe
public class Status extends AbstractModelObject {
	private volatile boolean ibConnected=false;
	private volatile boolean ibConnecting = false;
	private volatile boolean ibConnectingOrConnected = false;
	private volatile boolean ptlConnected=false;
	
	public boolean isIbConnected() {
		return ibConnected;
	}

	public void setIbConnected(boolean ibConnected) {
		boolean oldval=this.ibConnected;
		this.ibConnected = ibConnected;
		firePropertyChange("ibConnected", oldval, this.ibConnected);
		setIbConnectingOrConnected(ibConnecting || ibConnected);
	}

	public boolean isPtlConnected() {
		return ptlConnected;
	}

	public void setPtlConnected(boolean ptlConnected) {
		boolean oldval=this.ptlConnected;
		this.ptlConnected = ptlConnected;
		firePropertyChange("ptlConnected", oldval, this.ptlConnected);
	}

	public boolean isIbConnecting() {
		return ibConnecting;
	}

	public void setIbConnecting(boolean ibConnecting) {
		boolean oldval=this.ibConnecting;
		this.ibConnecting = ibConnecting;
		firePropertyChange("ibConnecting", oldval, this.ibConnecting);
		setIbConnectingOrConnected(ibConnecting || ibConnected);
	}

	public boolean isIbConnectingOrConnected() {
		return ibConnectingOrConnected;
	}

	public void setIbConnectingOrConnected(boolean ibConnectingOrConnected) {
		boolean oldval=this.ibConnectingOrConnected;
		this.ibConnectingOrConnected = ibConnectingOrConnected;
		firePropertyChange("ibConnectingOrConnected", oldval, this.ibConnectingOrConnected);
	}
	

}

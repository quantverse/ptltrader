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
package com.pairtradinglab.ptltrader.trading;
import net.jcip.annotations.*;

@Immutable
public class MultiRatio {
	private final double minRatio;
	private final double maxRatio;
	
	public MultiRatio(double minRatio, double maxRatio) {
		this.minRatio=minRatio;
		this.maxRatio=maxRatio;
	}
	
	public MultiRatio(double bid1, double ask1, double bid2, double ask2) {
		double maxprice1, minprice1, maxprice2, minprice2;
		
		maxprice1=ask1;
		if (bid1>maxprice1) maxprice1=bid1;
		minprice1=bid1;
		if (ask1<minprice1) minprice1=ask1;
		maxprice2=ask2;
		if (bid2>maxprice2) maxprice2=bid2;
		minprice2=bid2;
		if (ask2<minprice2) minprice2=ask2;
		
		minRatio=minprice1/maxprice2;
		maxRatio=maxprice1/minprice2;
	}
	
	public double getMinRatio() {
		return minRatio;
	}
	public double getMaxRatio() {
		return maxRatio;
	}
	
	

}

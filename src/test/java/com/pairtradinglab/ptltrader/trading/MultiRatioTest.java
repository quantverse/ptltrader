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
import static org.junit.Assert.*;
import org.junit.Test;

import com.pairtradinglab.ptltrader.trading.MultiRatio;


public class MultiRatioTest {

	@Test
	public void testMultiRatio1() {
		double minr, maxr;
		MultiRatio mr;
		mr = new MultiRatio(2.1, 2, 1, 1);
		minr=mr.getMinRatio();
		maxr=mr.getMaxRatio();
		//System.out.println(minr+" "+maxr);
		assertEquals(2, minr, 0.01);
		assertEquals(2.1, maxr, 0.01);
		mr = new MultiRatio(2.05, 2.1, 1, 1);
		minr=mr.getMinRatio();
		maxr=mr.getMaxRatio();
		assertEquals(2.05, minr, 0.01);
		assertEquals(2.1, maxr, 0.01);
		
	}
	
	
	@Test
	public void testMultiRatio2() {
		double minr, maxr;
		MultiRatio mr;
		mr = new MultiRatio(2, 2, 1.5, 1);
		minr=mr.getMinRatio();
		maxr=mr.getMaxRatio();
		//System.out.println(minr+" "+maxr);
		assertEquals(1.33, minr, 0.01);
		assertEquals(2, maxr, 0.01);
		mr = new MultiRatio(2, 2, 1.2, 1.5);
		minr=mr.getMinRatio();
		maxr=mr.getMaxRatio();
		//System.out.println(minr+" "+maxr);
		assertEquals(1.33, minr, 0.01);
		assertEquals(1.67, maxr, 0.01);
		
	}
	
	

}

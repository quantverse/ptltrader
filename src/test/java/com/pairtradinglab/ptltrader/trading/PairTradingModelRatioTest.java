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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.pairtradinglab.ptltrader.trading.PairTradingModel;
import com.pairtradinglab.ptltrader.trading.PairTradingModelRatio;
import org.apache.log4j.Logger;
import com.tictactec.ta.lib.MAType;

import org.joda.time.DateTime;

import static org.mockito.Mockito.mock;

public class PairTradingModelRatioTest {
	private PairTradingModelRatio mod;
	
	private double[] genPricesSin(int len, double mult, double offset, double freq) {
		double out[] = new double[len];
		for(int i=0; i<len; i++) {
			out[i]=offset+mult*Math.sin(freq*(double) i);
		}
		return out;
	}
	
	

	@Before
	public void setUp() throws Exception {
		//System.out.println("setup called");
		mod = new PairTradingModelRatio(new MarketRates(), new MarketRates(), mock(Logger.class));
		mod.initialize();
		
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetZScore() {
		double prices1[] = genPricesSin(35, 2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPricesSin(35, -2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setMaType(MAType.Sma);
		mod.setPrices(prices1, prices2);
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		mr1.setLast(8);
		mr2.setLast(12);
		mr1.setBid(8);
		mr2.setBid(12);
		mr1.setAsk(8);
		mr2.setAsk(12);
		double zscore = mod.getZScore(PairTradingModel.ZSCORE_AUTO);
		//System.out.println(zscore);
		assertEquals(-1.21359, zscore, 0.00001);
		
	}
	
	@Test
	public void testGetRsi() {
		double prices1[] = genPricesSin(35, 2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPricesSin(35, -2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setRsiPeriod(6);
		mod.setMaType(MAType.Sma);
		mod.setPrices(prices1, prices2);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		mr1.setBid(8);
		mr2.setBid(12);
		mr1.setAsk(8);
		mr2.setAsk(12);
		double rsi = mod.getRsi();
		assertEquals(38.57889, rsi, 0.0001);
		mr2.setBid(13);
		mr2.setAsk(13);
		rsi = mod.getRsi();
		assertEquals(37.5545, rsi, 0.0001);
		
	}

	@Test
	public void testEntryLogic() {
		double prices1[] = genPricesSin(35, 2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPricesSin(35, -2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setMaType(MAType.Sma);
		mod.setPrices(prices1, prices2);
		double price1=12;
		double price2=7;
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(7.2);
		mr2.setBid(6.9);
		int signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(-1, signal);
		assertEquals(2.201497, mod.getLastZscoreInvolved(), 0.01);
		
		mr2.setAsk(8.4);
		mr2.setBid(7.9);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(0, signal);
		assertEquals(2.201497, mod.getLastZscoreInvolved(), 0.01);
		
		price1=7;
		price2=18;
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(18.5);
		mr2.setBid(17.9);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(1, signal);
		assertEquals(-2.154811, mod.getLastZscoreInvolved(), 0.01);
		
		mr2.setAsk(14.5);
		mr2.setBid(14.9);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(0, signal);
		assertEquals(-2.154811, mod.getLastZscoreInvolved(), 0.01);
		
		
	}
	
	// temporarily skipped
	//@Test
	public void testEntryLogicRsi() {
		double prices1[] = genPricesSin(35, 2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPricesSin(35, -2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setMaType(MAType.Sma);
		mod.setRsiPeriod(6);
		mod.setRsiThreshold(43);
		mod.setPrices(prices1, prices2);
		double price1=12;
		double price2=7;
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(7.2);
		mr2.setBid(6.9);
		int signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(0, signal);
		mod.setRsiThreshold(42);
		signal = mod.entryLogic();
		assertEquals(-1, signal);
		assertEquals(2.201497, mod.getLastZscoreInvolved(), 0.01);
		
		mr2.setAsk(8.4);
		mr2.setBid(7.9);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(0, signal);
		assertEquals(2.201497, mod.getLastZscoreInvolved(), 0.01);
		
		price1=7;
		price2=18;
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(18.5);
		mr2.setBid(17.9);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(0, signal);
		mod.setRsiThreshold(8);
		signal = mod.entryLogic();
		assertEquals(1, signal);
		assertEquals(-2.154811, mod.getLastZscoreInvolved(), 0.01);
		
		mr2.setAsk(14.5);
		mr2.setBid(14.9);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(0, signal);
		assertEquals(-2.154811, mod.getLastZscoreInvolved(), 0.01);
		
		
	}

	@Test
	public void testGetLookbackRequired() {
		int lookback=mod.getLookbackRequired();
		assertEquals(49, lookback);
		
		mod.setRsiPeriod(50);
		lookback=mod.getLookbackRequired();
		assertEquals(74, lookback);
		
	}
	
	@Test
	public void testExitLogic() {
		double prices1[] = genPricesSin(35, 2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPricesSin(35, -2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setMaType(MAType.Sma);
		mod.setPrices(prices1, prices2);
		double price1=10;
		double price2=10;
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		mr1.setLast(price1);
		mr1.setAsk(10.5);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(price1);
		mr2.setBid(price2);
		boolean close;
		close = mod.exitLogic(PairTradingModel.SIGNAL_SHORT);
		//System.out.println(close);
		assertFalse(close);
		
		
		// check with threshold
		mr1.setAsk(price1);
		close = mod.exitLogic(PairTradingModel.SIGNAL_SHORT);
		//System.out.println(close);
		assertTrue(close);
		assertEquals(-0.071, mod.getLastZscoreInvolved(), 0.01);
		
		mod.storeReversalState();
		boolean rev = mod.checkReversalCondition();
		assertFalse(rev);
		
		
	}
	
	
	@Test
	public void testGetProfitPotential() {
		double prices1[] = genPricesSin(35, 2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPricesSin(35, -2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setMaType(MAType.Sma);
		mod.setPrices(prices1, prices2);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		mr1.setAsk(12);
		mr1.setBid(12);
		mr1.setLast(12);
		mr2.setAsk(7);
		mr2.setBid(7);
		mr2.setLast(7);
		
		double res;
		res=mod.getProfitPotential(10000, 0.5, 0.5);
		//System.out.println("potential: "+res);
		assertEquals(5403.59, res, 0.1);
		
		mr1.setAsk(7);
		mr1.setBid(7);
		mr1.setLast(7);
		mr2.setAsk(17);
		mr2.setBid(17);
		mr2.setLast(17);
		res=mod.getProfitPotential(10000, 0.5, 0.5);
		assertEquals(10391.74, res, 0.1);
		
		
	}
	
	@Test
	public void testReversalLogic() {
		double prices1[] = genPricesSin(35, 2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPricesSin(35, -2, 10, 3.1415927*0.25);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setMaType(MAType.Sma);
		mod.setPrices(prices1, prices2);
		double price1=12;
		double price2=7;
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(7.2);
		mr2.setBid(6.9);
		
		mod.storeReversalState();
		
		boolean rev = mod.checkReversalCondition();
		assertTrue(rev);
		
	}

	@Test
	public void testCalcLegQtys() {
		QtyQty res = mod.calcLegQtys(1000, 0.5, 0.1, 20, 15);
		assertEquals(83, res.qty1);
		assertEquals(111, res.qty2);
	}

}

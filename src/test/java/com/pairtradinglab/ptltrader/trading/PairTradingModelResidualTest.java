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
import static org.mockito.Mockito.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.log4j.Logger;
import com.tictactec.ta.lib.MAType;

public class PairTradingModelResidualTest {
	private PairTradingModelResidual mod;

	@Before
	public void setUp() throws Exception {
		mod = new PairTradingModelResidual(new MarketRates(), new MarketRates(), mock(Logger.class));
		mod.initialize();
		
	}

	@After
	public void tearDown() throws Exception {
	}
	
	
	private double[] genPrices(int len, double mult, double offset, double noiseFreq, double noiseMult) {
		double out[] = new double[len];
		for(int i=0; i<len; i++) {
			out[i]=offset+noiseMult*Math.sin(noiseFreq*(double) (i-1))+mult*(double) (i-1);
		}
		return out;
	}

	@Test
	public void testGetZScore() {
		double prices1[] = genPrices(31, 1, 10, 2, 0.3);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPrices(31, 2, 10, 3, 0.3);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setPrices(prices1, prices2);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		mr1.setLast(13.5);
		mr2.setLast(18);
		mr1.setBid(13.5);
		mr2.setBid(18);
		mr1.setAsk(13.5);
		mr2.setAsk(18);
		double zscore = mod.getZScore(PairTradingModel.ZSCORE_AUTO);
		//System.out.println(zscore);
		assertEquals(-2.116644, zscore, 0.0001);
		
		
		mr1.setLast(14);
		mr2.setLast(17.2);
		mr1.setBid(14);
		mr2.setBid(17.2);
		mr1.setAsk(14);
		mr2.setAsk(17.2);
		zscore = mod.getZScore(PairTradingModel.ZSCORE_AUTO);
		//System.out.println(zscore);
		assertEquals(1.6708805, zscore, 0.0001);
	}

	@Test
	public void testEntryLogic() {
		double prices1[] = genPrices(31, 1, 10, 2, 0.3);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPrices(31, 2, 10, 3, 0.3);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setPrices(prices1, prices2);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		double price1=13.6;
		double price2=18;
		mr1.setLast(price1);
		mr1.setAsk(13.7);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(price2);
		mr2.setBid(price2);
		
		int signal = mod.entryLogic();
		assertEquals(0, signal);
		
		
		mr1.setAsk(13.6);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(1, signal);
		assertEquals(-1.6959, mod.getLastZscoreInvolved(), 0.001);
		
		
		
		
		price1=14;
		price2=17.2;
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(17.3);
		mr2.setBid(price2);
		
		signal = mod.entryLogic();
		assertEquals(0, signal);
		
		
		mr2.setAsk(17.2);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(-1, signal);
		assertEquals(1.67088, mod.getLastZscoreInvolved(), 0.001);
				
	}
	
	@Test
	public void testEntryLogicUptick() {
		// FIXME we need more complex test here
		double prices1[] = genPrices(31, 1, 10, 2, 0.3);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPrices(31, 2, 10, 3, 0.3);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setEntryMode(PairTradingModel.ENTRY_MODE_UPTICK);
		mod.setPrices(prices1, prices2);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		double price1=13.6;
		double price2=18;
		mr1.setLast(price1);
		mr1.setAsk(13.7);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(price2);
		mr2.setBid(price2);
		
		int signal = mod.entryLogic();
		assertEquals(0, signal);
		
		
		mr1.setAsk(13.6);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(1, signal);
		assertEquals(-1.6959, mod.getLastZscoreInvolved(), 0.001);
		
		price1=14;
		price2=17.2;
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(17.3);
		mr2.setBid(price2);
		
		signal = mod.entryLogic();
		assertEquals(0, signal);
		
		
		mr2.setAsk(17.2);
		signal = mod.entryLogic();
		//System.out.println(signal);
		assertEquals(0, signal);
		assertEquals(-1.6959, mod.getLastZscoreInvolved(), 0.001);
				
	}
	
	@Test
	public void testEntryLogicDowntick() {
		// FIXME we need more complex test here
		double prices1[] = genPrices(31, 1, 10, 2, 0.3);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPrices(31, 2, 10, 3, 0.3);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setEntryMode(PairTradingModel.ENTRY_MODE_DOWNTICK);
		mod.setPrices(prices1, prices2);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		double price1=13.67;
		double price2=18;
		mr1.setLast(price1);
		mr1.setAsk(13.7);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(price2);
		mr2.setBid(price2);
		
		int signal = mod.entryLogic();
		assertEquals(0, signal);
		
		price1=14;
		price2=17.2;
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(17.3);
		mr2.setBid(price2);
		
		signal = mod.entryLogic();
		assertEquals(-1, signal);
		assertEquals(1.46038, mod.getLastZscoreInvolved(), 0.001);
				
	}

	@Test
	public void testExitLogic() {
		double prices1[] = genPrices(31, 1, 10, 2, 0.3);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPrices(31, 2, 10, 3, 0.3);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setExitThreshold(0.1);
		mod.setPrices(prices1, prices2);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		double price1=13.95;
		double price2=18;
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(price2);
		mr2.setBid(price2);
		
		//System.out.println(mod.getZScore());
		
		boolean close;
		close = mod.exitLogic(PairTradingModel.SIGNAL_LONG);
		assertFalse(close);
		
		mr1.setLast(14);
		mr1.setAsk(14);
		mr1.setBid(14);
		
		//System.out.println(mod.getZScore());
		close = mod.exitLogic(PairTradingModel.SIGNAL_LONG);
		assertTrue(close);
		
		
		mr1.setLast(14.05);
		mr1.setAsk(14.05);
		mr1.setBid(14.05);
		
		//System.out.println(mod.getZScore());
		close = mod.exitLogic(PairTradingModel.SIGNAL_SHORT);
		assertFalse(close);
		
		mr1.setLast(14);
		mr1.setAsk(14);
		mr1.setBid(14);
		
		//System.out.println(mod.getZScore());
		close = mod.exitLogic(PairTradingModel.SIGNAL_SHORT);
		assertTrue(close);
	}

	@Test
	public void testGetLookbackRequired() {
		assertEquals(31, mod.getLookbackRequired());
	}
	
	
	@Test
	public void testGetProfitPotential() {
		double prices1[] = genPrices(31, 1, 10, 2, 0.3);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPrices(31, 2, 10, 3, 0.3);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setPrices(prices1, prices2);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		double price1=13.6;
		double price2=18;
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(price2);
		mr2.setBid(price2);

		double res;
		res=mod.getProfitPotential(10000, 0.5, 0.5);
		//System.out.println("potential: "+res);
		assertEquals(595.301, res, 0.1);
		
		price1=14;
		price2=17.2;
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(price2);
		mr2.setBid(price2);
		
		res=mod.getProfitPotential(10000, 0.5, 0.5);
		//System.out.println("potential: "+res);
		assertEquals(602.98, res, 0.1);
		
		
	}
	
	@Test
	public void testGetProfitPotential2() {
		double prices1[] = {
		29.93,		
		29.95,
		28.99,
		29.68,
		29.86,
		28.73,
		27.82,
		28.67,
		29.02,
		28.51,
		27.88,
		28.01,
		28.29,
		28.76,
		29.89,
		30.32,
		30.09,
		30.01,
		29.88,
		30.51,
		30.65,
		30.52,
		30.7,
		31.74,
		32.39,
		30.79,
		30.43,
		29.56,
		29.7,
		29.97,
		29.28,
		29.37,
		27.65,
		27.12,
		27.56,
		27.71,
		27.83,
		28.52,
		28.04,
		28.48,
		29.32};
		
		double prices2[] = {
			29.95,
			29.97,
			29.68,
			29.44,
			28.62,
			27.28,
			27.7,
			27.27,
			26.94,
			26.66,
			25.65,
			25.71,
			26.58,
			26.67,
			26.49,
			26.89,
			26.81,
			25.96,
			25.1,
			24.45,
			24.58,
			23.94,
			23.66,
			23.67,
			22.72,
			22.02,
			21.28,
			22.09,
			21.66,
			22.01,
			22.38,
			22.2,
			22.05,
			22.35,
			22.74,
			22.54,
			22.27,
			23.12,
			22.46,
			22.27,
			23

		};
		
		for(int i = 0; i < prices1.length/2; i++) {
		    double temp = prices1[i];
		    prices1[i] = prices1[prices1.length - i - 1];
		    prices1[prices1.length - i - 1] = temp;
		}
		for(int i = 0; i < prices2.length/2; i++) {
		    double temp = prices2[i];
		    prices2[i] = prices2[prices2.length - i - 1];
		    prices2[prices2.length - i - 1] = temp;
		}
		
		mod.setEntryThreshold(1.6);
		mod.setLinRegPeriod(40);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();

		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setPrices(prices1, prices2);
		
		double price1=30.98;
		double price2=29.71;
		mr1.setLast(price1);
		mr2.setLast(price2);
		
		
		
		double res;
		res=mod.getProfitPotential(10000, 0.5, 0.5);
		//System.out.println("potential: "+res);
		assertEquals(0, res, 0.1);
		
		
		
		
	}
	
	@Test
	public void testGetProfitPotential3() {
		double prices1[] = genPrices(31, 5, 10, 2, 0.3);
		//for(int i=0;i<prices1.length;i++) System.out.print(prices1[i]+" "); System.out.println("");
		double prices2[] = genPrices(31, 1, 2, 2, 0.3);
		//for(int i=0;i<prices2.length;i++) System.out.print(prices2[i]+" "); System.out.println("");
		mod.setPrices(prices1, prices2);
		
		MarketRates mr1 = mod.getMr1();
		MarketRates mr2 = mod.getMr2();
		
		double price1=135;
		double price2=28;
		mr1.setLast(price1);
		mr1.setAsk(price1);
		mr1.setBid(price1);
		mr2.setLast(price2);
		mr2.setAsk(price2);
		mr2.setBid(price2);
		
		
		double res;
		res=mod.getProfitPotential(10000, 0.5, 0.5);
		//System.out.println("potential: "+res);
		assertEquals(534.33, res, 0.1);
		
		
	}


}

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

import com.pairtradinglab.ptltrader.model.PairStrategy;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PairTradingModelKalmanGridTest {
    private PairTradingModelKalmanGrid mod;

    private PairStrategy getStrategyMock() {
        PairStrategy ps = mock(PairStrategy.class);
        when(ps.getEntryThreshold()).thenReturn(2d);
        when(ps.getExitThreshold()).thenReturn(0.2);
        when(ps.getMaxEntryScore()).thenReturn(10d);
        when(ps.getAllowPositions()).thenReturn(PairStrategy.ALLOW_POSITIONS_BOTH);
        when(ps.isMaxDaysEnabled()).thenReturn(true);
        when(ps.getMaxDays()).thenReturn(20);
        when(ps.getEntryMode()).thenReturn(PairTradingModel.ENTRY_MODE_SIMPLE);
        when(ps.getNeutrality()).thenReturn(PairStrategy.NEUTRALITY_DOLLAR);
        return ps;
    }

    @Before
    public void setUp() throws Exception {
        mod = new PairTradingModelKalmanGrid(new MarketRates(), new MarketRates(), mock(Logger.class));
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

    private void basicCheck() {
        assertEquals(1.911166975412445, mod.grid.getBeta(), 0.00000001);
        assertEquals(0.116136338158041, mod.grid.getAlpha(), 0.00000001);
        assertEquals(0.24220538747859338, mod.grid.getSq(), 0.00000001);
        assertEquals(2.327291320416293, mod.lastScore, 0.00000001);
    }

    @Test
    public void testGetZScore() {
        double prices1[] = genPrices(100, 1, 10, 2, 0.3);
        double prices2[] = genPrices(100, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicCheck();
        double b0 = mod.grid.getBeta();
        double b1 = mod.grid.getAlpha();
        double sq = mod.grid.getSq();

        MarketRates mr1 = mod.getMr1();
        MarketRates mr2 = mod.getMr2();

        double p1 = 100;
        double p2 = p1 * b0 + b1;

        mr1.setBid(p1-0.1);
        mr2.setBid(p2-0.1);
        mr1.setAsk(p1+0.1);
        mr2.setAsk(p2+0.1);
        assertEquals(0, mod.getZScore(PairTradingModel.ZSCORE_AUTO), 0.0001);

        p2 = p1 * b0 + b1 + sq;

        mr1.setBid(p1-0.1);
        mr2.setBid(p2-0.1);
        mr1.setAsk(p1+0.1);
        mr2.setAsk(p2+0.1);
        assertEquals(-1, mod.getZScore(PairTradingModel.ZSCORE_AUTO), 0.0001);

        p2 = p1 * b0 + b1 - sq;

        mr1.setBid(p1-0.1);
        mr2.setBid(p2-0.1);
        mr1.setAsk(p1+0.1);
        mr2.setAsk(p2+0.1);
        assertEquals(1, mod.getZScore(PairTradingModel.ZSCORE_AUTO), 0.0001);

        // now try again to see if we get the same data (if grid is reinitialized properly)
        mod.setPrices(prices1, prices2);

        basicCheck();

    }

    @Test
    public void testEntryLogic() {
        double prices1[] = genPrices(100, 1, 10, 2, 0.3);
        double prices2[] = genPrices(100, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicCheck();
        double b0 = mod.grid.getBeta();
        double b1 = mod.grid.getAlpha();
        double sq = mod.grid.getSq();

        MarketRates mr1 = mod.getMr1();
        MarketRates mr2 = mod.getMr2();

        double p1 = 100;
        double p2 = p1 * b0 + b1;
        mr1.setBid(p1);
        mr2.setBid(p2-0.01);
        mr1.setAsk(p1);
        mr2.setAsk(p2+0.01);

        int res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_NONE, res);
        assertEquals(0, mod.getLastZscoreInvolved(), 0.1);

        mr2.setBid(p1 * b0 + b1 + 2.01*sq);
        mr2.setAsk(mr2.getBid());

        res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_LONG, res);
        assertEquals(-2, mod.getLastZscoreInvolved(), 0.1);

        mr2.setBid(p1 * b0 + b1 - 2.01*sq);
        mr2.setAsk(mr2.getBid());

        res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_SHORT, res);
        assertEquals(2, mod.getLastZscoreInvolved(), 0.1);

    }

    @Test
    public void testEntryLogicUptick() {
        double prices1[] = genPrices(100, 1, 10, 2, 0.3);
        double prices2[] = genPrices(100, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        when(ps.getEntryMode()).thenReturn(PairTradingModel.ENTRY_MODE_UPTICK);
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicCheck();
        double b0 = mod.grid.getBeta();
        double b1 = mod.grid.getAlpha();
        double sq = mod.grid.getSq();

        MarketRates mr1 = mod.getMr1();
        MarketRates mr2 = mod.getMr2();

        double p1 = 100;
        double p2 = p1 * b0 + b1;
        mr1.setBid(p1);
        mr2.setBid(p2-0.01);
        mr1.setAsk(p1);
        mr2.setAsk(p2+0.01);

        int res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_NONE, res);
        assertEquals(0, mod.getLastZscoreInvolved(), 0.1);

        mr2.setBid(p1 * b0 + b1 + 2.01*sq);
        mr2.setAsk(mr2.getBid());

        res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_LONG, res);
        assertEquals(-2, mod.getLastZscoreInvolved(), 0.1);

        mr2.setBid(p1 * b0 + b1 - 2.01*sq);
        mr2.setAsk(mr2.getBid());

        res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_NONE, res);
        assertEquals(-2, mod.getLastZscoreInvolved(), 0.1);
    }

    @Test
    public void testEntryLogicDowntick() {
        double prices1[] = genPrices(100, 1, 10, 2, 0.3);
        double prices2[] = genPrices(100, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        when(ps.getEntryMode()).thenReturn(PairTradingModel.ENTRY_MODE_DOWNTICK);
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicCheck();
        double b0 = mod.grid.getBeta();
        double b1 = mod.grid.getAlpha();
        double sq = mod.grid.getSq();

        MarketRates mr1 = mod.getMr1();
        MarketRates mr2 = mod.getMr2();

        double p1 = 100;
        double p2 = p1 * b0 + b1;
        mr1.setBid(p1);
        mr2.setBid(p2-0.01);
        mr1.setAsk(p1);
        mr2.setAsk(p2+0.01);

        int res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_NONE, res);
        assertEquals(0, mod.getLastZscoreInvolved(), 0.1);

        mr2.setBid(p1 * b0 + b1 + 2.01*sq);
        mr2.setAsk(mr2.getBid());

        res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_NONE, res);
        assertEquals(0, mod.getLastZscoreInvolved(), 0.1);

        mr2.setBid(p1 * b0 + b1 - 2.01*sq);
        mr2.setAsk(mr2.getBid());

        res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_NONE, res);
        assertEquals(0, mod.getLastZscoreInvolved(), 0.1);

        mr2.setBid(p1 * b0 + b1 - 1.9*sq);
        mr2.setAsk(mr2.getBid());

        res = mod.entryLogic();
        assertEquals(PairTradingModel.SIGNAL_SHORT, res);
        assertEquals(1.9, mod.getLastZscoreInvolved(), 0.1);

    }

    @Test
    public void testExitLogic() {
        double prices1[] = genPrices(100, 1, 10, 2, 0.3);
        double prices2[] = genPrices(100, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicCheck();
        double b0 = mod.grid.getBeta();
        double b1 = mod.grid.getAlpha();
        double sq = mod.grid.getSq();

        MarketRates mr1 = mod.getMr1();
        MarketRates mr2 = mod.getMr2();

        double p1 = 100;
        mr1.setBid(p1);
        mr2.setBid(p1 * b0 + b1 + 0.21*sq);
        mr1.setAsk(p1);
        mr2.setAsk(p1 * b0 + b1 + 0.21*sq);

        boolean res = mod.exitLogic(PairTradingModel.SIGNAL_NONE);
        assertFalse(res);
        assertEquals(0, mod.getLastZscoreInvolved(), 0.1);
        res = mod.exitLogic(PairTradingModel.SIGNAL_LONG);
        assertFalse(res);
        assertEquals(0, mod.getLastZscoreInvolved(), 0.1);
        res = mod.exitLogic(PairTradingModel.SIGNAL_SHORT);
        assertTrue(res);
        assertEquals(-0.2, mod.getLastZscoreInvolved(), 0.1);

        mr2.setBid(p1 * b0 + b1 - 0.21*sq);
        mr1.setAsk(p1);
        mr2.setAsk(p1 * b0 + b1 - 0.21*sq);

        res = mod.exitLogic(PairTradingModel.SIGNAL_NONE);
        assertFalse(res);
        assertEquals(-0.2, mod.getLastZscoreInvolved(), 0.1);
        res = mod.exitLogic(PairTradingModel.SIGNAL_LONG);
        assertTrue(res);
        assertEquals(0.2, mod.getLastZscoreInvolved(), 0.1);
        res = mod.exitLogic(PairTradingModel.SIGNAL_SHORT);
        assertFalse(res);
        assertEquals(0.2, mod.getLastZscoreInvolved(), 0.1);
    }

    @Test(expected = IllegalStateException.class)
    public void testGetLookbackRequired() {
        int lb = mod.getLookbackRequired();
    }

    @Test
    public void testGetLookbackRequiredSetUp() {
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        assertEquals(97, mod.getLookbackRequired());
    }


    @Test
    public void testGetProfitPotential() {
        double prices1[] = genPrices(100, 1, 10, 2, 0.3);
        double prices2[] = genPrices(100, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicCheck();
        double b0 = mod.grid.getBeta();
        double b1 = mod.grid.getAlpha();
        double sq = mod.grid.getSq();

        MarketRates mr1 = mod.getMr1();
        MarketRates mr2 = mod.getMr2();

        double p1 = 100 - 0.05 * sq / b0;
        double p2 = 100 * b0 + b1 + 0.05 * sq;
        mr1.setBid(p1);
        mr2.setBid(p2);
        mr1.setAsk(p1);
        mr2.setAsk(p2);

        double res = mod.getProfitPotential(10000, 0.5, 0.5);
        assertEquals(0, res, 0.001);

        p1 = 100 - 0.55 * sq / b0;
        p2 = 100 * b0 + b1 + 0.55 * sq;
        mr1.setBid(p1);
        mr2.setBid(p2);
        mr1.setAsk(p1);
        mr2.setAsk(p2);

        res = mod.getProfitPotential(10000, 0.5, 0.5);
        assertEquals(36.15384590251779, res, 0.001);

        p1 = 100 + 0.55 * sq / b0;
        p2 = 100 * b0 + b1 - 0.55 * sq;
        mr1.setBid(p1);
        mr2.setBid(p2);
        mr1.setAsk(p1);
        mr2.setAsk(p2);

        res = mod.getProfitPotential(10000, 0.5, 0.5);
        assertEquals(35.73563138282262, res, 0.001);

    }

    @Test
    public void testCalcLegQtysDollarNeutral() {
        double prices1[] = genPrices(100, 1, 10, 2, 0.3);
        double prices2[] = genPrices(100, 5, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        double b0 = mod.grid.getBeta();
        assertEquals(4.635520265930075, b0, 0.00000001);
        double b1 = mod.grid.getAlpha();
        assertEquals(0.22567459829183648, b1, 0.00000001);
        double sq = mod.grid.getSq();
        assertEquals(0.42654471149814993, sq, 0.00000001);

        double p1 = 100 - 2 * sq / b0;
        double p2 = 100 * b0 + b1 + 2 * sq;

        QtyQty res = mod.calcLegQtys(10000, 0.5, 0.5, p1, p2);
        assertEquals(100, res.qty1);
        assertEquals(21, res.qty2);
        assertTrue((res.qty1*p1 + res.qty2*p2) <= 20000);

    }

    @Test
    public void testCalcLegQtysDollarBeta() {
        double prices1[] = genPrices(100, 1, 10, 2, 0.3);
        double prices2[] = genPrices(100, 5, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        when(ps.getNeutrality()).thenReturn(PairStrategy.NEUTRALITY_BETA);
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        double b0 = mod.grid.getBeta();
        assertEquals(4.635520265930075, b0, 0.00000001);
        double b1 = mod.grid.getAlpha();
        assertEquals(0.22567459829183648, b1, 0.00000001);
        double sq = mod.grid.getSq();
        assertEquals(0.42654471149814993, sq, 0.00000001);

        double p1 = 100 - 2 * sq / b0;
        double p2 = 100 * b0 + b1 + 2 * sq;

        QtyQty res = mod.calcLegQtys(10000, 0.5, 0.5, p1, p2);
        assertEquals(99, res.qty1);
        assertEquals(21, res.qty2);
        assertTrue((res.qty1*p1 + res.qty2*p2) <= 20000);

    }

}

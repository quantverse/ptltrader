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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PairTradingModelKalmanAutoTest {
    private PairTradingModelKalmanAuto mod;

    public static class TestWrap {
        private AbstractPairTradingModelState something;

        public AbstractPairTradingModelState getSomething() {
            return something;
        }

        public void setSomething(AbstractPairTradingModelState something) {
            this.something = something;
        }

        public TestWrap() {
        }
    }

    private PairStrategy getStrategyMock() {
        PairStrategy ps = mock(PairStrategy.class);
        when(ps.getMaxEntryScore()).thenReturn(10d);
        when(ps.getAllowPositions()).thenReturn(PairStrategy.ALLOW_POSITIONS_BOTH);
        when(ps.isMaxDaysEnabled()).thenReturn(true);
        when(ps.getMaxDays()).thenReturn(20);
        when(ps.getEntryMode()).thenReturn(PairTradingModel.ENTRY_MODE_SIMPLE);
        when(ps.getNeutrality()).thenReturn(PairStrategy.NEUTRALITY_DOLLAR);
        when(ps.getKalmanAutoVe()).thenReturn(0.001);
        when(ps.getKalmanAutoUsageTarget()).thenReturn(60d);
        return ps;
    }

    @Before
    public void setUp() throws Exception {
        mod = new PairTradingModelKalmanAuto(new MarketRates(), new MarketRates(), mock(Logger.class));
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

    private void basicModelCheck() {
        assertEquals(-4.625, mod.grid.getCurrentDelta(), 0.001);
        assertEquals(1.934608470267768, mod.grid.getBeta(), 0.00000001);
        assertEquals(0.13212487294251068, mod.grid.getAlpha(), 0.00000001);
        assertEquals(0.7214843080775014, mod.grid.getSq(), 0.00000001);
        assertEquals(-1.2429170825865246, mod.lastScore, 0.00000001);
    }

    @Test
    public void testGetZScore() {
        double prices1[] = genPrices(140, 1, 10, 2, 0.3);
        double prices2[] = genPrices(140, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicModelCheck();

        double b0 = mod.grid.getBeta();
        double b1 = mod.grid.getAlpha();
        double sq = mod.grid.getSq();

        MarketRates mr1 = mod.getMr1();
        MarketRates mr2 = mod.getMr2();

        double p1 = 100;
        double p2 = p1 * b0 + b1;

        mr1.setBid(p1-0.01);
        mr2.setBid(p2-0.01);
        mr1.setAsk(p1+0.01);
        mr2.setAsk(p2+0.01);
        assertEquals(0, mod.getZScore(PairTradingModel.ZSCORE_AUTO), 0.0001);

        p2 = p1 * b0 + b1 + sq;

        mr1.setBid(p1-0.01);
        mr2.setBid(p2-0.01);
        mr1.setAsk(p1+0.01);
        mr2.setAsk(p2+0.01);
        assertEquals(-0.95932, mod.getZScore(PairTradingModel.ZSCORE_AUTO), 0.0001);

        p2 = p1 * b0 + b1 - sq;

        mr1.setBid(p1-0.01);
        mr2.setBid(p2-0.01);
        mr1.setAsk(p1+0.01);
        mr2.setAsk(p2+0.01);
        assertEquals(0.95932, mod.getZScore(PairTradingModel.ZSCORE_AUTO), 0.0001);

        // now try again to see if we get the same data (if grid is reinitialized properly)
        mod.setPrices(prices1, prices2);

        basicModelCheck();
    }

    @Test
    public void testEntryLogic() {
        double prices1[] = genPrices(140, 1, 10, 2, 0.3);
        double prices2[] = genPrices(140, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicModelCheck();

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
    public void testExitLogic() {
        double prices1[] = genPrices(140, 1, 10, 2, 0.3);
        double prices2[] = genPrices(140, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicModelCheck();

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
        assertEquals(139, mod.getLookbackRequired());
    }


    @Test
    public void testGetProfitPotential() {
        double prices1[] = genPrices(140, 1, 10, 2, 0.3);
        double prices2[] = genPrices(140, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        basicModelCheck();

        double b0 = mod.grid.getBeta();
        double b1 = mod.grid.getAlpha();
        double sq = mod.grid.getSq();

        MarketRates mr1 = mod.getMr1();
        MarketRates mr2 = mod.getMr2();

        double p1 = 100;
        double p2 = 100 * b0 + b1;
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
        assertEquals(102.8311, res, 0.001);

        p1 = 100 + 0.55 * sq / b0;
        p2 = 100 * b0 + b1 - 0.55 * sq;
        mr1.setBid(p1);
        mr2.setBid(p2);
        mr1.setAsk(p1);
        mr2.setAsk(p2);

        res = mod.getProfitPotential(10000, 0.5, 0.5);
        assertEquals(101.6004, res, 0.001);

    }

    @Test
    public void testCalcLegQtysDollarNeutral() {
        double prices1[] = genPrices(140, 1, 10, 2, 0.3);
        double prices2[] = genPrices(140, 5, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        assertEquals(-3.625, mod.grid.getCurrentDelta(), 0.001);
        double b0 = mod.grid.getBeta();
        assertEquals(4.733415492297649, b0, 0.00000001);
        double b1 = mod.grid.getAlpha();
        assertEquals(0.2383183639233368, b1, 0.00000001);
        double sq = mod.grid.getSq();
        assertEquals(2.277804349866021, sq, 0.00000001);
        assertEquals(-1.1039373900451976, mod.lastScore, 0.00000001);

        double p1 = 100 - 2 * sq / b0;
        double p2 = 100 * b0 + b1 + 2 * sq;

        QtyQty res = mod.calcLegQtys(10000, 0.5, 0.5, p1, p2);
        assertEquals(100, res.qty1);
        assertEquals(20, res.qty2);
        assertTrue((res.qty1*p1 + res.qty2*p2) <= 20000);

    }

    @Test
    public void testCalcLegQtysDollarBeta() {
        double prices1[] = genPrices(140, 1, 10, 2, 0.3);
        double prices2[] = genPrices(140, 5, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        when(ps.getNeutrality()).thenReturn(PairStrategy.NEUTRALITY_BETA);
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        assertEquals(-3.625, mod.grid.getCurrentDelta(), 0.001);
        double b0 = mod.grid.getBeta();
        assertEquals(4.733415492297649, b0, 0.00000001);
        double b1 = mod.grid.getAlpha();
        assertEquals(0.2383183639233368, b1, 0.00000001);
        double sq = mod.grid.getSq();
        assertEquals(2.277804349866021, sq, 0.00000001);
        assertEquals(-1.1039373900451976, mod.lastScore, 0.00000001);

        double p1 = 100 - 2 * sq / b0;
        double p2 = 100 * b0 + b1 + 2 * sq;

        QtyQty res = mod.calcLegQtys(10000, 0.5, 0.5, p1, p2);
        assertEquals(99, res.qty1);
        assertEquals(21, res.qty2);
        assertTrue((res.qty1*p1 + res.qty2*p2) <= 20000);

    }

    @Test
    public void testLockLogic() {
        double prices1[] = genPrices(140, 1, 10, 2, 0.3);
        double prices2[] = genPrices(140, 2, 10, 3, 0.3);
        PairStrategy ps = getStrategyMock();
        mod.setupFromStrategy(ps);
        mod.setPrices(prices1, prices2);

        assertEquals(-4.625, mod.grid.getCurrentDelta(), 0.001);
        PairTradingModelKalmanAutoState st = (PairTradingModelKalmanAutoState) mod.getCurrentState();
        assertEquals(67, st.subModelId);

        mod.lockState(new PairTradingModelKalmanAutoState(50));
        PairTradingModelKalmanAutoState st2 = (PairTradingModelKalmanAutoState) mod.getCurrentState();
        assertEquals(50, st2.subModelId);
        assertEquals(-6.75, mod.grid.getCurrentDelta(), 0.001);
        assertEquals(1.9335730275008196, mod.grid.getBeta(), 0.00000001);
        assertEquals(0.08686177054369819, mod.grid.getAlpha(), 0.00000001);
        assertEquals(0.07565427555080334, mod.grid.getSq(), 0.00000001);
        assertEquals(-1.2429170825865246, mod.lastScore, 0.00000001); // this is last score from unlocked mode and it is fine to have it unchanged! not needed for exit logic

        // now setPrices again, to see if the lock still works
        mod.setPrices(prices1, prices2);

        PairTradingModelKalmanAutoState st3 = (PairTradingModelKalmanAutoState) mod.getCurrentState();
        assertEquals(50, st3.subModelId);
        assertEquals(-6.75, mod.grid.getCurrentDelta(), 0.001);
        assertEquals(1.9335730275008196, mod.grid.getBeta(), 0.00000001);
        assertEquals(0.08686177054369819, mod.grid.getAlpha(), 0.00000001);
        assertEquals(0.07565427555080334, mod.grid.getSq(), 0.00000001);
        assertEquals(-1.2429170825865246, mod.lastScore, 0.00000001); // this is last score from unlocked mode and it is fine to have it unchanged! not needed for exit logic

        // now unlock
        mod.unlockState();
        basicModelCheck();

        // json stuff
        AbstractPairTradingModelState sto = mod.getCurrentState();
        ObjectMapper mapper = new ObjectMapper();
        TestWrap tw = new TestWrap();
        tw.setSomething(sto);

        try {
            String serialized = mapper.writeValueAsString(tw);
            assertEquals("{\"something\":{\"@class\":\".PairTradingModelKalmanAutoState\",\"subModelId\":67}}", serialized);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        try {
            TestWrap tw2 = mapper.readValue("{\"something\":{\"@class\":\"com.pairtradinglab.ptltrader.trading.PairTradingModelKalmanAutoState\",\"subModelId\":111}}", TestWrap.class);
            assertEquals("com.pairtradinglab.ptltrader.trading.PairTradingModelKalmanAutoState", tw2.getSomething().getClass().getCanonicalName());
        } catch (IOException e) {
            e.printStackTrace();
        }

//        ObjectNode node = mapper.createObjectNode();
//        node.set("last_model_state", mapper.valueToTree(tw));
//        String json = node.toString();
//        System.out.println(json);
    }
}

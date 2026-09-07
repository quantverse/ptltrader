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
package com.pairtradinglab.ptltrader.store;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.pairtradinglab.ptltrader.model.LegHistoryEntry;
import com.pairtradinglab.ptltrader.model.TradeHistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.HistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.TransactionEvent;

public class HistoryPersistenceTest {
	private File dbFile;
	private Database db;

	@Before
	public void setUp() throws Exception {
		dbFile = File.createTempFile("ptltrader-hist", ".db");
		assertTrue(dbFile.delete());
		db = new Database(dbFile, mock(Logger.class));
		db.open();
	}

	@After
	public void tearDown() throws Exception {
		if (db != null) db.close();
		dbFile.delete();
		new File(dbFile.getAbsolutePath() + "-wal").delete();
		new File(dbFile.getAbsolutePath() + "-shm").delete();
	}

	@Test
	public void testTradeHistoryRoundTrip() throws Exception {
		DateTime dt = new DateTime(2026, 3, 1, 14, 30, 0, DateTimeZone.UTC);
		HistoryEntry he = new HistoryEntry("S1", "DU123456", dt, "NYSE:V", "NYSE:MA",
				HistoryEntry.ACTION_CLOSED, 250.5, 1.75, 4.0, 2.15, "ok");
		SqlitePortfolioStore.insertTradeHistory(db.getConnection(), he);

		List<TradeHistoryEntry> out = SqlitePortfolioStore.readTradeHistory(db.getConnection(), 100);
		assertEquals(1, out.size());
		TradeHistoryEntry e = out.get(0);
		assertEquals("NYSE:V", e.getStock1());
		assertEquals("NYSE:MA", e.getStock2());
		assertEquals(HistoryEntry.ACTION_CLOSED, e.getAction());
		assertEquals("DU123456", e.getAccount());
		assertEquals(250.5, e.getRealizedPl(), 1e-9);
		assertEquals(1.75, e.getRealizedPlPercent(), 1e-9);
		assertEquals(4.0, e.getCommissions(), 1e-9);
		assertEquals(2.15, e.getZscore(), 1e-9);
		assertEquals("ok", e.getComment());
		assertEquals(dt.getMillis(), e.getDatetime().getMillis());
	}

	@Test
	public void testLegHistoryRoundTrip() throws Exception {
		DateTime dt = new DateTime(2026, 3, 1, 14, 30, 0, DateTimeZone.UTC);
		TransactionEvent te = new TransactionEvent("S1", "DU123456", "NYSE:V",
				TransactionEvent.DIRECTION_LONG, 100, 12.5);
		te.setDatetime(dt);
		te.setRealizedPl(33.25);
		te.setCommissions(1.5);
		te.setFillTime(Duration.millis(1234));
		SqlitePortfolioStore.insertLegHistory(db.getConnection(), te);

		List<LegHistoryEntry> out = SqlitePortfolioStore.readLegHistory(db.getConnection(), 100);
		assertEquals(1, out.size());
		LegHistoryEntry e = out.get(0);
		assertEquals("NYSE:V", e.getSymbol());
		assertEquals(LegHistoryEntry.ACTION_BUY, e.getAction());
		assertEquals(100, e.getQty());
		assertEquals(12.5, e.getPrice(), 1e-9);
		assertEquals(1250.0, e.getValue(), 1e-9);
		assertEquals(33.25, e.getRealizedPl(), 1e-9);
		assertEquals(1.5, e.getCommissions(), 1e-9);
		assertEquals(1234, e.getFillTime().getMillis());
		assertEquals("DU123456", e.getAccount());
	}

	@Test
	public void testShortDirectionMapsToSell() throws Exception {
		TransactionEvent te = new TransactionEvent("S1", "A", "NYSE:MA",
				TransactionEvent.DIRECTION_SHORT, 50, 20.0);
		te.setDatetime(new DateTime(DateTimeZone.UTC));
		te.setFillTime(Duration.ZERO);
		SqlitePortfolioStore.insertLegHistory(db.getConnection(), te);
		assertEquals(LegHistoryEntry.ACTION_SELL,
				SqlitePortfolioStore.readLegHistory(db.getConnection(), 10).get(0).getAction());
	}

	@Test
	public void testNullFillTimeIsTolerated() throws Exception {
		TransactionEvent te = new TransactionEvent("S1", "A", "NYSE:MA",
				TransactionEvent.DIRECTION_LONG, 10, 5.0);
		te.setDatetime(new DateTime(DateTimeZone.UTC));
		// fillTime deliberately left null: a transaction can be recorded before
		// the fill duration is known.
		SqlitePortfolioStore.insertLegHistory(db.getConnection(), te);
		assertEquals(0, SqlitePortfolioStore.readLegHistory(db.getConnection(), 10)
				.get(0).getFillTime().getMillis());
	}

	@Test
	public void testReadsNewestFirstAndRespectsLimit() throws Exception {
		for (int i = 0; i < 5; i++) {
			HistoryEntry he = new HistoryEntry("S" + i, "A",
					new DateTime(2026, 1, 1 + i, 0, 0, 0, DateTimeZone.UTC),
					"NYSE:A", "NYSE:B", HistoryEntry.ACTION_CLOSED, 0, 0, 0, 0, "c" + i);
			SqlitePortfolioStore.insertTradeHistory(db.getConnection(), he);
		}
		List<TradeHistoryEntry> out = SqlitePortfolioStore.readTradeHistory(db.getConnection(), 3);
		assertEquals(3, out.size());
		assertEquals("newest row must come first", "c4", out.get(0).getComment());
		assertEquals("c3", out.get(1).getComment());
		assertEquals("c2", out.get(2).getComment());
	}
}

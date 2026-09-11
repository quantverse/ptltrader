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
package com.pairtradinglab.ptltrader;

import java.util.concurrent.ThreadPoolExecutor;

import org.apache.log4j.Logger;

import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;
import com.pairtradinglab.ptltrader.model.PortfolioList;
import com.pairtradinglab.ptltrader.store.PortfolioStore;

/**
 * The part of shutdown that must happen after the trading cores have stopped and
 * before the container stops the store, so that nothing already on its way to the
 * local database is lost.
 */
public final class ShutdownSequence {

	static final long BUS_DRAIN_TIMEOUT_MS = 10000;
	private static final long POLL_MS = 20;

	private ShutdownSequence() {
	}

	public static void beforeContainerStop(ThreadPoolExecutor busExecutor, PortfolioList portfolios,
			PortfolioStore store, Logger logger) throws InterruptedException {
		// A HistoryEntry, TransactionEvent or PairStateUpdated posted by a core just
		// before it exited may still be queued on the async bus. Stopping the store
		// first would drop it: the delivery would find db-worker gone, or
		// busExecutor.shutdownNow() would discard it.
		if (!awaitIdle(busExecutor, BUS_DRAIN_TIMEOUT_MS)) {
			logger.error("event bus still busy after " + BUS_DRAIN_TIMEOUT_MS
					+ "ms at shutdown; events still in flight may not be saved");
		}
		// Edits are otherwise synced out only on the once-a-minute beacon, so one made
		// in the last minute before exit would never reach the store.
		for (Portfolio p : portfolios.getPortfolios()) {
			if (hasUnsyncedEdits(p)) {
				store.savePortfolio(p);
			}
		}
	}

	private static boolean hasUnsyncedEdits(Portfolio p) {
		if (p.isDirty()) return true;
		for (PairStrategy s : p.getPairStrategies()) {
			if (s.isDirty()) return true;
		}
		return false;
	}

	/**
	 * Waits until the executor has no running or queued task. Idle must be seen twice
	 * in a row: a running delivery can post a follow-up event (a BeaconFlash becoming a
	 * sync-out request) that is queued only as it finishes.
	 */
	static boolean awaitIdle(ThreadPoolExecutor executor, long timeoutMs) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		int idleSeen = 0;
		while (System.currentTimeMillis() < deadline) {
			if (executor.getActiveCount() == 0 && executor.getQueue().isEmpty()) {
				if (++idleSeen >= 2) return true;
			} else {
				idleSeen = 0;
			}
			Thread.sleep(POLL_MS);
		}
		return false;
	}
}

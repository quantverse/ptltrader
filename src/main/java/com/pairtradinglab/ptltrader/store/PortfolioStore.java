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

import com.fasterxml.jackson.databind.JsonNode;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;

/**
 * Local replacement for PtlApiClient: owns loading and persisting portfolios,
 * strategy runtime state and history.
 */
public interface PortfolioStore {

	/** Loads every portfolio into the PortfolioList and initializes it. */
	void load();

	/** Queues a configuration save for one portfolio and its strategies. */
	void savePortfolio(Portfolio p);

	/** Queues a runtime state save for one strategy. */
	void saveStrategyState(PairStrategy s);

	/** Queues removal of one strategy's state and rewrites its portfolio document. */
	void deleteStrategy(PairStrategy s);

	/** Queues removal of a whole portfolio and its strategy state. */
	void deletePortfolio(Portfolio p);

	/**
	 * Queues an upsert of a validated, freshly re-uid'd portfolio document. Does
	 * not reload the PortfolioList; the caller must call flush() and then load()
	 * once it has finished inserting so the new portfolio(s) appear in the UI.
	 */
	void insertPortfolioDocument(JsonNode document);

	/**
	 * Binds a portfolio to an IB account, enforcing that no other portfolio holds
	 * the same account. Posts StoreProblem(BIND_DENIED) and does nothing on conflict.
	 */
	void bindPortfolioToAccount(Portfolio p, String accountCode);

	/** Blocks until every queued write has been applied. For shutdown and tests. */
	void flush();
}

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

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The checks that matter only for documents read back from the database, where the
 * importer's re-uid and state stripping never ran: a stored document that fails any
 * of them would otherwise throw inside updateFromJson and block every portfolio from
 * loading.
 */
public class PortfolioDocumentValidatorTest {

	private static final String STRATEGY = "{"
		+ "\"uid\":\"S1\",\"model\":\"Ratio\",\"ticker1\":\"NYSE:V\",\"ticker2\":\"NYSE:MA\","
		+ "\"trade_as_1\":0,\"trade_as_2\":0,"
		+ "\"entry_threshold\":2.0,\"exit_threshold\":0.0,\"downtick_threshold\":0.0,\"max_score\":10.0,"
		+ "\"ratio_ma_type\":1,\"ratio_ma_period\":15,\"ratio_stddev_period\":15,\"ratio_entry_mode\":0,"
		+ "\"ratio_rsi_period\":10,\"ratio_rsi_threshold\":0.0,\"residual_linreg_period\":30,"
		+ "\"neutrality\":0,\"ka_ve\":0.001,\"ka_usage_target\":60.0,"
		+ "\"ticker1margin\":50.0,\"ticker2margin\":50.0,"
		+ "\"last_opened_datetime\":null,\"last_opened_equity\":null,\"last_model_state\":null,"
		+ "\"allow_reversals\":1,\"enable_max_days\":1,\"max_days\":20,"
		+ "\"enable_min_pl\":0,\"min_pl\":0.0,\"enable_min_price\":1,\"min_price\":5.0,"
		+ "\"enable_min_profit_potential\":1,\"min_profit_potential\":40.0,"
		+ "\"entry_start_hour\":9,\"entry_start_minute\":30,\"entry_end_hour\":15,\"entry_end_minute\":55,"
		+ "\"exit_start_hour\":9,\"exit_start_minute\":30,\"exit_end_hour\":15,\"exit_end_minute\":55,"
		+ "\"timezone\":\"America/New_York\",\"allow_positions\":0,\"status\":2,\"slot_occupation\":1.0"
		+ "}";

	private static final String PORTFOLIO = "{"
		+ "\"uid\":\"P1\",\"name\":\"My Portfolio\",\"account_code\":\"DU123456\","
		+ "\"max_pairs_open\":10,\"master_status\":2,\"pdt_rules\":1,\"account_alloc\":100,"
		+ "\"strategies\":[" + STRATEGY + "]}";

	private final ObjectMapper mapper = new ObjectMapper();

	private ObjectNode strategyOf(ObjectNode portfolio) {
		return (ObjectNode) portfolio.get("strategies").get(0);
	}

	@Test
	public void testAcceptsAValidStoredDocument() throws Exception {
		PortfolioDocumentValidator.validate(mapper.readTree(PORTFOLIO));
	}

	@Test(expected = PortfolioDocumentValidator.InvalidDocumentException.class)
	public void testRejectsStrategyWithoutUid() throws Exception {
		ObjectNode doc = (ObjectNode) mapper.readTree(PORTFOLIO);
		strategyOf(doc).remove("uid");
		PortfolioDocumentValidator.validate(doc);
	}

	@Test
	public void testRejectsMalformedLastOpenedDatetime() throws Exception {
		ObjectNode doc = (ObjectNode) mapper.readTree(PORTFOLIO);
		strategyOf(doc).put("last_opened_datetime", "yesterday");
		try {
			PortfolioDocumentValidator.validate(doc);
			fail("expected InvalidDocumentException");
		} catch (PortfolioDocumentValidator.InvalidDocumentException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("last_opened_datetime"));
		}
	}

	@Test
	public void testAcceptsWellFormedLastOpenedDatetime() throws Exception {
		ObjectNode doc = (ObjectNode) mapper.readTree(PORTFOLIO);
		strategyOf(doc).put("last_opened_datetime", "2026-03-01 14:30:00");
		PortfolioDocumentValidator.validate(doc);
	}
}

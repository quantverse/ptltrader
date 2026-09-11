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

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PortfolioImporterTest {

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

	private ObjectMapper mapper;

	@Before
	public void setUp() {
		mapper = new ObjectMapper();
	}

	@Test
	public void testAcceptsArrayForm() throws Exception {
		List<ObjectNode> out = PortfolioImporter.parse("[" + PORTFOLIO + "]", mapper);
		assertEquals(1, out.size());
		assertEquals("My Portfolio", out.get(0).get("name").asText());
	}

	@Test
	public void testAcceptsSingleObjectForm() throws Exception {
		assertEquals(1, PortfolioImporter.parse(PORTFOLIO, mapper).size());
	}

	@Test
	public void testAssignsFreshUids() throws Exception {
		ObjectNode p = PortfolioImporter.parse(PORTFOLIO, mapper).get(0);
		assertNotEquals("portfolio uid must be regenerated", "P1", p.get("uid").asText());
		assertNotEquals("strategy uid must be regenerated", "S1",
				p.get("strategies").get(0).get("uid").asText());
	}

	@Test
	public void testTwoImportsOfTheSameFileDoNotCollide() throws Exception {
		String a = PortfolioImporter.parse(PORTFOLIO, mapper).get(0).get("uid").asText();
		String b = PortfolioImporter.parse(PORTFOLIO, mapper).get(0).get("uid").asText();
		assertNotEquals(a, b);
	}

	@Test
	public void testClearsAccountCode() throws Exception {
		ObjectNode p = PortfolioImporter.parse(PORTFOLIO, mapper).get(0);
		assertEquals("an imported binding is meaningless locally", "", p.get("account_code").asText());
	}

	@Test
	public void testStripsRuntimeState() throws Exception {
		ObjectNode s = (ObjectNode) PortfolioImporter.parse(PORTFOLIO, mapper).get(0)
				.get("strategies").get(0);
		for (String f : PortfolioDocuments.STATE_FIELDS) {
			assertFalse("imported state belongs to no account: " + f, s.has(f));
		}
	}

	@Test
	public void testRejectsAnUnrecognizedModel() throws Exception {
		String broken = PORTFOLIO.replace("\"model\":\"Ratio\"", "\"model\":\"Ratio-v9\"");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			// An unknown model is not fatal at runtime - the core falls through to
			// PairTradingModelDummy - so a pair with one would silently never trade.
			// The message must name the offending strategy and the bad value.
			assertTrue(e.getMessage(), e.getMessage().contains("NYSE:V/NYSE:MA"));
			assertTrue(e.getMessage(), e.getMessage().contains("Ratio-v9"));
		}
	}

	@Test
	public void testAcceptsEveryKnownModel() throws Exception {
		for (String model : PortfolioImporter.VALID_MODELS) {
			String json = PORTFOLIO.replace("\"model\":\"Ratio\"", "\"model\":\"" + model + "\"");
			assertEquals(model, PortfolioImporter.parse(json, mapper).get(0)
					.get("strategies").get(0).get("model").asText());
		}
	}

	@Test
	public void testRejectsMissingStrategyField() throws Exception {
		String broken = PORTFOLIO.replace("\"entry_threshold\":2.0,", "");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue("message must name the field", e.getMessage().contains("entry_threshold"));
			assertTrue("message must name the strategy", e.getMessage().contains("NYSE:V"));
		}
	}

	private String rejectionMessage(String document) throws Exception {
		try {
			PortfolioImporter.parse(document, mapper);
		} catch (PortfolioImporter.InvalidImportException e) {
			return e.getMessage();
		}
		fail("expected InvalidImportException");
		return null;
	}

	/**
	 * updateFromJson resolves ratio_ma_type with MAType.values()[n.asInt()], so an
	 * out-of-range value used to be accepted here and then throw on every later
	 * startup, leaving the whole database unloadable.
	 */
	@Test
	public void testRejectsRatioMaTypeOutsideTheKnownRange() throws Exception {
		String high = rejectionMessage(PORTFOLIO.replace("\"ratio_ma_type\":1", "\"ratio_ma_type\":999"));
		assertTrue(high, high.contains("ratio_ma_type") && high.contains("NYSE:V"));
		String negative = rejectionMessage(PORTFOLIO.replace("\"ratio_ma_type\":1", "\"ratio_ma_type\":-1"));
		assertTrue(negative, negative.contains("ratio_ma_type"));
	}

	/** An unknown zone id makes DateTimeZone.forID throw when the portfolio loads. */
	@Test
	public void testRejectsUnknownTimezone() throws Exception {
		String message = rejectionMessage(PORTFOLIO.replace("\"timezone\":\"America/New_York\"",
				"\"timezone\":\"Not/AZone\""));
		assertTrue(message, message.contains("Not/AZone") && message.contains("NYSE:V"));
	}

	/**
	 * A string where a number belongs does not throw - asDouble() quietly turns it
	 * into 0.0 - so it would import as a strategy with an entry threshold of zero.
	 */
	@Test
	public void testRejectsNonNumericValueInNumericField() throws Exception {
		String message = rejectionMessage(PORTFOLIO.replace("\"entry_threshold\":2.0", "\"entry_threshold\":\"abc\""));
		assertTrue(message, message.contains("entry_threshold") && message.contains("NYSE:V"));
	}

	@Test
	public void testRejectsMissingPortfolioField() throws Exception {
		String broken = PORTFOLIO.replace("\"max_pairs_open\":10,", "");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("max_pairs_open"));
		}
	}

	@Test
	public void testRejectsUnparseableSymbol() throws Exception {
		String broken = PORTFOLIO.replace("\"ticker1\":\"NYSE:V\"", "\"ticker1\":\"NOTASYMBOL\"");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("NOTASYMBOL"));
		}
	}

	@Test
	public void testRejectsUnknownExchange() throws Exception {
		String broken = PORTFOLIO.replace("\"ticker2\":\"NYSE:MA\"", "\"ticker2\":\"XETRA:BMW\"");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException for an unsupported exchange");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("XETRA:BMW"));
		}
	}

	@Test
	public void testRejectsGarbage() throws Exception {
		try {
			PortfolioImporter.parse("not json at all", mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			// expected
		}
	}

	@Test
	public void testRejectsMissingStrategiesArray() throws Exception {
		String broken = PORTFOLIO.replace(",\"strategies\":[" + STRATEGY + "]", "");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("strategies"));
		}
	}

	@Test
	public void testAnExportedDocumentReImportsCleanly() throws Exception {
		// Closes the loop with PortfolioDocuments.build: whatever export writes,
		// import must accept. A field added to the bean but missing from
		// REQUIRED_STRATEGY_FIELDS, or vice versa, breaks here.
		// Portfolio.updateFromJson() only reaches strategyFactory.createForPortfolio()
		// for a uid it doesn't already have (Portfolio.java:402-406). The brief's
		// original ordering called pf.updateFromJson(PORTFOLIO) - whose strategies
		// array names uid "S1" - before any strategy with that uid existed on pf,
		// with a null strategyFactory (this test has no container to supply a real
		// one). That is an unconditional NPE, not a corner case: findStrategyByUid()
		// returns null on an empty portfolio, so the null-factory branch always runs.
		// Fixing it: add the "S1" strategy to pf first, so updateFromJson() takes the
		// existing-strategy branch (s.updateFromJson(rs)) and never touches the
		// factory. That also makes the separate st.updateFromJson(STRATEGY) call
		// redundant, since pf.updateFromJson(PORTFOLIO) already applies it once
		// through the strategy's own uid match; the duplicate call is removed rather
		// than left in to silently reapply the same fields twice.
		com.pairtradinglab.ptltrader.LoggerFactory lf =
				new com.pairtradinglab.ptltrader.LoggerFactoryImpl(
						new com.pairtradinglab.ptltrader.RuntimeParams(new String[] { "unittest" }));
		com.pairtradinglab.ptltrader.model.Portfolio pf =
				new com.pairtradinglab.ptltrader.model.Portfolio(null, null, lf, "P1");

		com.pairtradinglab.ptltrader.model.PairStrategy st =
				new com.pairtradinglab.ptltrader.model.PairStrategy(
						"S1", pf, "NYSE:V", "NYSE:MA", 0, 0, null, null);
		pf.addPairStrategy(st);

		pf.updateFromJson(mapper.readTree(PORTFOLIO));

		ObjectNode exported = PortfolioDocuments.build(pf, mapper);
		List<ObjectNode> reimported = PortfolioImporter.parse(exported.toString(), mapper);

		assertEquals(1, reimported.size());
		assertEquals(1, reimported.get(0).get("strategies").size());
		assertEquals("NYSE:V", reimported.get(0).get("strategies").get(0).get("ticker1").asText());
		assertEquals(2.0, reimported.get(0).get("strategies").get(0).get("entry_threshold").asDouble(), 1e-9);
	}

	@Test
	public void testAMalformedFileIsRejectedWhole() throws Exception {
		String second = STRATEGY.replace("\"timezone\":\"America/New_York\",", "");
		String twoStrategies = PORTFOLIO.replace(
				"\"strategies\":[" + STRATEGY + "]",
				"\"strategies\":[" + STRATEGY + "," + second + "]");
		try {
			PortfolioImporter.parse(twoStrategies, mapper);
			fail("a file with one bad strategy must be rejected entirely");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("timezone"));
		}
	}
}

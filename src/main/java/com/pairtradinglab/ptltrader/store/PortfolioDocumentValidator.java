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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.tictactec.ta.lib.MAType;

/**
 * Checks that a portfolio document can be loaded by Portfolio.updateFromJson() and
 * PairStrategy.updateFromJson() without throwing or silently corrupting a value.
 *
 * Used on import, so a file that would break the next startup is refused, and on
 * load, so one bad stored document is skipped instead of stopping every portfolio
 * from loading. The checks mirror exactly what those methods do with each field:
 * get() on a missing field throws, MAType.values()[n.asInt()] throws out of range,
 * DateTimeZone.forID() throws on an unknown zone, and asInt()/asDouble() quietly turn
 * a non-numeric value into zero.
 */
final class PortfolioDocumentValidator {

	static final class InvalidDocumentException extends Exception {
		private static final long serialVersionUID = 1L;

		InvalidDocumentException(String message) {
			super(message);
		}
	}

	/** Strategy fields read as text. Every other required strategy field is numeric. */
	private static final Set<String> STRATEGY_TEXT_FIELDS = new HashSet<String>(
			Arrays.asList("model", "ticker1", "ticker2", "timezone"));

	/** Read with path(...).asInt(default), so they may be absent - but not non-numeric. */
	private static final String[] OPTIONAL_STRATEGY_NUMERIC_FIELDS = { "ratio_rsi_period", "ratio_rsi_threshold" };

	private static final String[] PORTFOLIO_NUMERIC_FIELDS = { "max_pairs_open", "account_alloc", "master_status", "pdt_rules" };

	private PortfolioDocumentValidator() {
	}

	static void validate(JsonNode document) throws InvalidDocumentException {
		if (document == null || !document.isObject()) {
			throw new InvalidDocumentException("Expected a portfolio object.");
		}
		String portfolio = "Portfolio \"" + document.path("name").asText("(unnamed)") + "\"";
		for (String f : PortfolioImporter.REQUIRED_PORTFOLIO_FIELDS) {
			if (!document.has(f)) {
				throw new InvalidDocumentException(String.format(
						"%s is missing the required field \"%s\".", portfolio, f));
			}
		}
		for (String f : PORTFOLIO_NUMERIC_FIELDS) {
			requireNumber(document, f, portfolio);
		}
		JsonNode strategies = document.get("strategies");
		if (strategies == null || !strategies.isArray()) {
			throw new InvalidDocumentException(portfolio + " has no strategies array.");
		}
		for (JsonNode strategy : strategies) {
			if (!strategy.isObject()) {
				throw new InvalidDocumentException(portfolio + " contains a strategy that is not an object.");
			}
			validateStrategy(strategy);
		}
	}

	private static void validateStrategy(JsonNode s) throws InvalidDocumentException {
		String strategy = "Strategy " + s.path("ticker1").asText("?") + "/" + s.path("ticker2").asText("?");
		if (!s.path("uid").isTextual()) {
			throw new InvalidDocumentException(strategy + " has no uid.");
		}
		for (String f : PortfolioImporter.REQUIRED_STRATEGY_FIELDS) {
			if (!s.has(f)) {
				throw new InvalidDocumentException(String.format(
						"%s is missing the required field \"%s\".", strategy, f));
			}
			if (!STRATEGY_TEXT_FIELDS.contains(f)) {
				requireNumber(s, f, strategy);
			}
		}
		for (String f : OPTIONAL_STRATEGY_NUMERIC_FIELDS) {
			if (s.has(f) && !s.get(f).isNull()) {
				requireNumber(s, f, strategy);
			}
		}

		JsonNode maType = s.get("ratio_ma_type");
		if (!maType.isIntegralNumber() || maType.asInt() < 0 || maType.asInt() >= MAType.values().length) {
			throw new InvalidDocumentException(String.format(
					"%s has an invalid ratio_ma_type %s (expected 0 to %d).",
					strategy, maType, MAType.values().length - 1));
		}

		String timezone = s.get("timezone").asText();
		try {
			DateTimeZone.forID(timezone);
		} catch (IllegalArgumentException e) {
			throw new InvalidDocumentException(String.format(
					"%s has an unknown timezone \"%s\".", strategy, timezone));
		}

		// Runtime state is only present on documents read back from the database.
		JsonNode lastOpened = s.get("last_opened_datetime");
		if (lastOpened != null && !lastOpened.isNull()) {
			try {
				DateTimeFormat.forPattern(StrategyState.DATETIME_FORMAT).withZoneUTC()
						.parseDateTime(lastOpened.asText());
			} catch (IllegalArgumentException e) {
				throw new InvalidDocumentException(String.format(
						"%s has a malformed last_opened_datetime \"%s\".", strategy, lastOpened.asText()));
			}
		}
		JsonNode lastEquity = s.get("last_opened_equity");
		if (lastEquity != null && !lastEquity.isNull()) {
			requireNumber(s, "last_opened_equity", strategy);
		}
	}

	/** Booleans are accepted: our own export writes the enable_* flags as true/false. */
	private static void requireNumber(JsonNode node, String field, String owner) throws InvalidDocumentException {
		JsonNode v = node.get(field);
		if (v == null || !(v.isNumber() || v.isBoolean())) {
			throw new InvalidDocumentException(String.format(
					"%s has a non-numeric value for \"%s\": %s", owner, field, v));
		}
	}
}

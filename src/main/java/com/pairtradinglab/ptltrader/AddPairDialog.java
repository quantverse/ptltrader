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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.trading.ContractExt;

/**
 * Prompts for the two legs of a new pair, their instrument types and the model.
 *
 * Symbols are validated here rather than at first trade, so an unsupported
 * exchange is rejected while the user can still fix it.
 */
public class AddPairDialog extends Dialog {

	public static class Result {
		public final String stock1;
		public final String stock2;
		public final int tradeAs1;
		public final int tradeAs2;
		public final String model;

		public Result(String stock1, String stock2, int tradeAs1, int tradeAs2, String model) {
			this.stock1 = stock1;
			this.stock2 = stock2;
			this.tradeAs1 = tradeAs1;
			this.tradeAs2 = tradeAs2;
			this.model = model;
		}
	}

	private static final String[] MODELS = {
		PairStrategy.MODEL_RATIO, PairStrategy.MODEL_RESIDUAL,
		PairStrategy.MODEL_KALMAN_GRID, PairStrategy.MODEL_KALMAN_AUTO
	};
	/** Index matches PairStrategy's trade_as values: 0 = stock, 1 = CFD. */
	private static final String[] INSTRUMENTS = { "Stock", "CFD" };

	private Text textStock1;
	private Text textStock2;
	private Combo comboTradeAs1;
	private Combo comboTradeAs2;
	private Combo comboModel;
	private Result result;

	public AddPairDialog(Shell parentShell) {
		super(parentShell);
	}

	public Result getResult() {
		return result;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Add Pair");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		Composite c = new Composite(area, SWT.NONE);
		c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		c.setLayout(new GridLayout(3, false));

		new Label(c, SWT.NONE).setText("Stock 1:");
		textStock1 = new Text(c, SWT.BORDER);
		textStock1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		textStock1.setMessage("EXCHANGE:TICKER, e.g. NYSE:V");
		comboTradeAs1 = new Combo(c, SWT.READ_ONLY);
		comboTradeAs1.setItems(INSTRUMENTS);
		comboTradeAs1.select(0);

		new Label(c, SWT.NONE).setText("Stock 2:");
		textStock2 = new Text(c, SWT.BORDER);
		textStock2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		textStock2.setMessage("EXCHANGE:TICKER, e.g. NYSE:MA");
		comboTradeAs2 = new Combo(c, SWT.READ_ONLY);
		comboTradeAs2.setItems(INSTRUMENTS);
		comboTradeAs2.select(0);

		new Label(c, SWT.NONE).setText("Model:");
		comboModel = new Combo(c, SWT.READ_ONLY);
		comboModel.setItems(MODELS);
		comboModel.select(0);
		comboModel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		return area;
	}

	@Override
	protected void okPressed() {
		String s1 = textStock1.getText().trim().toUpperCase();
		String s2 = textStock2.getText().trim().toUpperCase();

		String bad = validate(s1);
		if (bad == null) bad = validate(s2);
		if (bad != null) {
			MessageDialog.openError(getShell(), "Invalid Symbol", bad);
			return;
		}
		if (s1.equals(s2)) {
			MessageDialog.openError(getShell(), "Invalid Pair", "The two legs must be different symbols.");
			return;
		}

		result = new Result(s1, s2, comboTradeAs1.getSelectionIndex(),
				comboTradeAs2.getSelectionIndex(), comboModel.getText());
		super.okPressed();
	}

	private String validate(String symbol) {
		if (symbol.isEmpty()) return "Both symbols are required.";
		try {
			ContractExt.createFromGoogleSymbol(symbol, false);
			return null;
		} catch (RuntimeException e) {
			return String.format("\"%s\" is not a supported EXCHANGE:TICKER symbol.", symbol);
		}
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "Add", true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}
}

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

import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;

/**
 * Prompts for the name of a new portfolio.
 */
public class NewPortfolioDialog {
	private final Shell parent;

	public NewPortfolioDialog(Shell parent) {
		this.parent = parent;
	}

	/**
	 * @return the entered name, or null if the user cancelled
	 */
	public String open() {
		InputDialog dlg = new InputDialog(parent, "New Portfolio", "Portfolio name:", "",
				new IInputValidator() {
					@Override
					public String isValid(String newText) {
						if (newText == null || newText.trim().isEmpty()) return "The name must not be empty.";
						if (newText.length() > 255) return "The name is too long.";
						return null;
					}
				});
		if (dlg.open() != Window.OK) return null;
		return dlg.getValue().trim();
	}
}

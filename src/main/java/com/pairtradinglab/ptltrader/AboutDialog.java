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

import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.SWT;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

public class AboutDialog extends Dialog {

	protected Object result;
	protected Shell shell;

	/**
	 * Create the dialog.
	 * @param parent
	 * @param style
	 */
	public AboutDialog(Shell parent, int style) {
		super(parent, SWT.DIALOG_TRIM | SWT.PRIMARY_MODAL | SWT.RESIZE);
		setText("About PTL Trader");
	}

	/**
	 * Open the dialog.
	 * @return the result
	 */
	public Object open() {
		createContents();
		shell.open();
		shell.layout();
		Display display = getParent().getDisplay();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return result;
	}

	/**
	 * Create contents of the dialog.
	 */
	private void createContents() {
		shell = new Shell(getParent(), getStyle());
		shell.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		shell.setSize(520, 450);
		shell.setText(getText());
		shell.setLayout(new GridLayout(1, false));
		
		Label lblAboutImage = new Label(shell, SWT.NONE);
		lblAboutImage.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblAboutImage.setImage(SWTResourceManager.getImage(AboutDialog.class, "/com/pairtradinglab/ptltrader/logo-smaller.png"));
		
		Label lblProductName = new Label(shell, SWT.NONE);
		lblProductName.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblProductName.setForeground(SWTResourceManager.getColor(SWT.COLOR_BLACK));
		lblProductName.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblProductName.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblProductName.setText("PTL Trader");
		
		Label lblProductVersion = new Label(shell, SWT.NONE);
		lblProductVersion.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblProductVersion.setForeground(SWTResourceManager.getColor(SWT.COLOR_BLACK));
		lblProductVersion.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblProductVersion.setText("Version: " + Version.getVersion());
		
		Label lblProductCopyright = new Label(shell, SWT.NONE);
		lblProductCopyright.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblProductCopyright.setForeground(SWTResourceManager.getColor(SWT.COLOR_BLACK));
		lblProductCopyright.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblProductCopyright.setText("\u00A9 2011-2023 Quantverse OÜ");
		
		StyledText aboutText = new StyledText(shell, SWT.WRAP);
		aboutText.setDoubleClickEnabled(false);
		aboutText.setBottomMargin(10);
		aboutText.setLeftMargin(10);
		aboutText.setRightMargin(10);
		aboutText.setTopMargin(10);
		aboutText.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		aboutText.setForeground(SWTResourceManager.getColor(SWT.COLOR_DARK_GRAY));
		String licence_notice = "This program is free software: you can redistribute it and/or modify\n" +
				"it under the terms of the GNU General Public License as published by\n" +
				"the Free Software Foundation, either version 3 of the License, or\n" +
				"(at your option) any later version.\n" +
				"\n" +
				"This program is distributed in the hope that it will be useful,\n" +
				"but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
				"MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n" +
				"GNU General Public License for more details.";
		aboutText.setText(licence_notice);
		aboutText.setEditable(false);
		aboutText.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true, 1, 1));
		aboutText.setBounds(0, 0, 69, 19);
		
		Button btnClose = new Button(shell, SWT.NONE);
		btnClose.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});
		btnClose.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		btnClose.setBounds(0, 0, 75, 25);
		btnClose.setText("Close");

	}
}

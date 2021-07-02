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
/*******************************************************************************
 * Copyright (c) 2007, 2008 IST - Instituto Superior Tecnico
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pedro Santos - initial API and implementation
 *******************************************************************************/

import org.eclipse.core.databinding.observable.Diffs;
import org.eclipse.core.databinding.observable.IObservable;
import org.eclipse.core.databinding.observable.value.AbstractObservableValue;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.ProgressBar;


/**
 * {@link IObservable} implementation that wraps a {@link ProgressBar} widget.
 * 
 * @author Pedro Santos (pedro.miguel.santos@ist.utl.pt)
 * @version $Id: ProgressBarObservableValue.java,v 1.3 2008-05-09 15:36:13 pmrsa Exp $
 */
public class ProgressBarObservableValue extends AbstractObservableValue {
	public static final int ATTR_SELECTION = 0;
	public static final int ATTR_MAX = 1;
	public static final int ATTR_MIN = 2;
	
    private final ProgressBar _progress;

    private final int attribute;

    private boolean updating = false;

    private int currentSelection;

    private SelectionListener listener;

    /**
     * @param _progress
     * @param attribute
     */
    public ProgressBarObservableValue(ProgressBar progress, int attribute) {
        super();
        this._progress = progress;
        this.attribute = attribute;
        
        if (attribute==ATTR_SELECTION) {
            currentSelection = progress.getSelection();
            // progress.addSelectionListener(listener = new
            // SelectionAdapter() {
            // @Override
            // public void widgetSelected(SelectionEvent e) {
            // if (!updating) {
            // int newSelection = ProgressBarObservableValue.this._progress
            // .getSelection();
            // fireValueChange(Diffs.createValueDiff(new Integer(
            // currentSelection), new Integer(newSelection)));
            // currentSelection = newSelection;
            // }
            // }
            // });
        } else if (attribute!=ATTR_MIN
                && attribute!=ATTR_MAX) {
            throw new IllegalArgumentException(
                    "Attribute name not valid: " + attribute); //$NON-NLS-1$
        }
    }

    @Override
    public void doSetValue(final Object value) {
        int oldValue;
        int newValue;
        try {
            updating = true;
            newValue = ((Integer) value).intValue();
            if (attribute==ATTR_SELECTION) {
                oldValue = _progress.getSelection();
                _progress.setSelection(newValue);
                currentSelection = newValue;
            } else if (attribute==ATTR_MIN) {
                oldValue = _progress.getMinimum();
                _progress.setMinimum(newValue);
            } else if (attribute==ATTR_MAX) {
                oldValue = _progress.getMaximum();
                _progress.setMaximum(newValue);
            } else {
                //Assert.isTrue(false, "invalid attribute name:" + attribute); //$NON-NLS-1$
                return;
            }
            fireValueChange(Diffs.createValueDiff(new Integer(oldValue),
                    new Integer(newValue)));
        } finally {
            updating = false;
        }
    }

    @Override
    public Object doGetValue() {
        int value = 0;
        if (attribute==ATTR_SELECTION) {
            value = _progress.getSelection();
        } else if (attribute==ATTR_MIN) {
            value = _progress.getMinimum();
        } else if (attribute==ATTR_MAX) {
            value = _progress.getMaximum();
        }
        return new Integer(value);
    }

    @Override
    public Object getValueType() {
        return Integer.TYPE;
    }

    /**
     * @return attribute being observed
     */
    public int getAttribute() {
        return attribute;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.core.databinding.observable.value.AbstractObservableValue#dispose()
     */
    @Override
    public synchronized void dispose() {
        super.dispose();

        // if (listener != null && !_progress.isDisposed()) {
        // _progress.removeSelectionListener(listener);
        // }
        listener = null;
    }
}

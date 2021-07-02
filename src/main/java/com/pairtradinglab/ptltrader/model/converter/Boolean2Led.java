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
package com.pairtradinglab.ptltrader.model.converter;

import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.swt.graphics.Image;
import org.eclipse.wb.swt.SWTResourceManager;

import com.pairtradinglab.ptltrader.Application;

public class Boolean2Led extends Converter {
	public Boolean2Led() {
		this(java.lang.Boolean.class, Image.class);
	}
	
	public Boolean2Led(Class<Boolean> class1, Class<Image> class2) {
		super(class1, class2);
	}

	@Override
	public Object convert(Object arg0) {
		Boolean v = (Boolean) arg0;
		if (v) {
			return SWTResourceManager.getImage(Application.class, "/com/pairtradinglab/ptltrader/Green1.png");
		} else {
			return SWTResourceManager.getImage(Application.class, "/com/pairtradinglab/ptltrader/Red1.png");
		}
	}

}

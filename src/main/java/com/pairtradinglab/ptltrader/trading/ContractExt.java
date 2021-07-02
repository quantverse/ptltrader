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
package com.pairtradinglab.ptltrader.trading;

import java.util.Vector;

import com.ib.client.Contract;
import com.ib.client.Util;

public class ContractExt extends Contract {
	
	public ContractExt() {
		super();
	}
	
	
	public boolean equalsInSymbolTypeCurrency(Object p_other) {
		if (this == p_other) {
    		return true;
    	}

    	if (p_other == null || !(p_other instanceof Contract)) {
    		return false;
    	}

        Contract l_theOther = (Contract)p_other;
        
        if (m_conId != l_theOther.m_conId) {
        	//return false;
        }

        if (Util.StringCompare(m_secType, l_theOther.m_secType) != 0) {
        	return false;
        }

        if (Util.StringCompare(m_symbol, l_theOther.m_symbol) != 0 ||
        	Util.StringCompare(m_currency, l_theOther.m_currency) != 0) {
        	return false;
        }
        
        return true;
	}
	
	public static ContractExt createFromGoogleSymbol(String s, boolean useCfd) {
		String r[] = s.split(":");
		if (r.length!=2) throw new IllegalArgumentException(String.format("Wrong instrument symbol [%s]", s));
		ContractExt out = new ContractExt();
		out.m_symbol=r[1].replace(".", " "); // RDS.A -> RDS A, etc...
		if (useCfd) {
			// this is going to be CFD
			out.m_secType="CFD";
		} else {
			// let's use STK (standard stock/ETF definition)
			out.m_secType="STK";
			out.m_primaryExch = "ISLAND";
		}

		out.m_exchange="SMART";
		if ("NYSE".equals(r[0])) {
			out.m_currency="USD";
		} else if ("NASDAQ".equals(r[0])) {
			out.m_currency="USD";
		} else if ("NYSEARCA".equals(r[0])) {
			out.m_currency="USD";
		} else if ("NYSEAMEX".equals(r[0])) {
			out.m_currency="USD";
		} else if ("NYSEMKT".equals(r[0])) {
			out.m_currency="USD";
		} else throw new IllegalArgumentException(String.format("Unsupported exchange [%s]", r[0]));
		return out;
	}

	@Override
	public int hashCode() {
		String hash = String.format("%s_%s_%s_%s", m_symbol, m_secType, m_exchange, m_currency);
		return hash.hashCode();
	}
	
	

}


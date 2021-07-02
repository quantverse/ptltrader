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

public class OlsCalculator {
		
	private double f(int j, int i, int offset, double[] prices2) {
		if (j==0) {
			return 1;
		} else {
			return prices2[offset+i];
		}
	}
	
	private double calcSumA(int j, int k, int linRegPeriod, int offset, double[] prices2) {
		double out=0;
		for(int i=0;i<linRegPeriod;i++) {
			out+=f(j, i, offset, prices2)*f(k, i, offset, prices2);
		}
		return out;
	}
	
	
	private double calcSumB(int j, int linRegPeriod, int offset, double[] prices1, double[] prices2) {
		double out=0;
		for(int i=0;i<linRegPeriod;i++) {
			out+=f(j, i, offset, prices2)*prices1[offset+i];
		}
		return out;
	}
	
	
	public OlsResult calculate(int linRegPeriod, int lag, double[] prices1, double[] prices2) {
		if (prices1.length<linRegPeriod) throw new IllegalArgumentException(String.format("Prices array must have at least %d items", linRegPeriod));
		
		int offset = prices1.length-linRegPeriod-lag;
		
		// calculate linear regression
		
		// setup matrix a
		double a[][] = new double[2][2];
		for(int j=0;j<2;j++) {
			for(int k=0;k<2;k++) {
				a[j][k]=calcSumA(j, k, linRegPeriod, offset, prices2);
			}
		}

		// setup vector b
		double b[] = new double[2];
		for(int j=0;j<2;j++) {
			b[j]=calcSumB(j, linRegPeriod, offset, prices1, prices2);
		}

		// inverse matrix
		for (int k=0; k<1; k++)
	      for (int i=k+1; i<=1; i++) 
	      {
	        b[i] -= a[i][k] / a[k][k] * b[k];
	        for (int j=k+1; j<=1; j++)
	          a[i][j] -= a[i][k] / a[k][k] * a[k][j];
	      }
	        
	    // ---- Compute coefficients using inverse of c ----
	    double X[] = new double[2];
	    
	    X[1] = b[1] / a[1][1];
	    for (int i=0; i>=0; i--)  
	    {
	      double s = 0;
	      for (int k=i+1; k<=1; k++)
	        s += a[i][k] * X[k];
	      X[i] = (b[i] - s) / a[i][i];
	    }

		double A=X[1];
		double B=X[0];
		
		// now we have to calculate the stddev of the spread series
		double sqsum=0;
		for (int i=0; i<linRegPeriod; i++) {
			double mean=A*prices2[offset+i]+B;
			sqsum+=(prices1[offset+i]-mean)*(prices1[offset+i]-mean);
		}
		double stdDev=Math.sqrt(sqsum/(double) linRegPeriod);
		
		double spread;
		if (lag>0) {
			spread = prices1[prices1.length-lag]-(A*prices2[prices2.length-lag]+B);
		} else spread=0;
		
		OlsResult out = new OlsResult(A, B, stdDev, spread);
		
		//System.out.println(String.format("lag=%d: A: %f B: %f stddev: %f spread: %f", lag, A, B, stdDev, spread));
		return out;
		
	}

}

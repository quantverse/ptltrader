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

import net.jcip.annotations.Immutable;

@Immutable
class TripleBandLogic {
    final double inThreshold;
    final double outThreshold;
    final double downTickThreshold;
    final double maxScore;
    final int entryMode;

    TripleBandLogic(double inThreshold, double outThreshold, double downTickThreshold, double maxScore, int entryMode) {
        this.inThreshold = inThreshold;
        this.outThreshold = outThreshold;
        this.downTickThreshold = downTickThreshold;
        this.maxScore = maxScore;
        this.entryMode = entryMode;
    }

    EntrySignal entryLogic(double scoreAsk, double scoreBid, double lastScoreAsk, double lastScoreBid) {
        int out = PairTradingModel.SIGNAL_NONE;

        if (entryMode == PairTradingModel.ENTRY_MODE_UPTICK) {
            // uptick
            if (scoreAsk <= -inThreshold && (maxScore<0.01 || scoreAsk>-maxScore) && lastScoreAsk > -inThreshold) out = PairTradingModel.SIGNAL_LONG;
            else if (scoreBid >= inThreshold && (maxScore<0.01 || scoreBid<maxScore) && lastScoreBid < inThreshold) out = PairTradingModel.SIGNAL_SHORT;

        } else if (entryMode == PairTradingModel.ENTRY_MODE_DOWNTICK) {
            // downtick
            if (scoreAsk > -inThreshold && scoreAsk<-downTickThreshold && (maxScore<0.01 || scoreAsk>-maxScore) && lastScoreAsk <= -inThreshold) out = PairTradingModel.SIGNAL_LONG;
            else if (scoreBid < inThreshold && scoreBid>downTickThreshold && (maxScore<0.01 || scoreBid<maxScore) && lastScoreBid >= inThreshold) out = PairTradingModel.SIGNAL_SHORT;
        } else {
            // simple
            if (scoreAsk <= -inThreshold && (maxScore<0.01 || scoreAsk>-maxScore)) out = PairTradingModel.SIGNAL_LONG;
            else if (scoreBid >= inThreshold && (maxScore<0.01 || scoreBid<maxScore)) out = PairTradingModel.SIGNAL_SHORT;
        }

        double zscoreInvolved = 0;
        if (out == PairTradingModel.SIGNAL_LONG) zscoreInvolved = scoreAsk;
        else if (out == PairTradingModel.SIGNAL_SHORT) zscoreInvolved = scoreBid;

        return new EntrySignal(out, zscoreInvolved);
    }

    ExitSignal exitLogic(int position, double scoreAsk, double scoreBid) {
        if (position==PairTradingModel.SIGNAL_SHORT && scoreAsk<=outThreshold) {
            return new ExitSignal(true, scoreAsk);
        } else if (position==PairTradingModel.SIGNAL_LONG && scoreBid>=-outThreshold) {
            return new ExitSignal(true, scoreBid);
        } else {
            return new ExitSignal(false, 0);
        }
    }

    boolean checkReversal(double score, double lastExitScore) {
        return Math.abs(lastExitScore)>=inThreshold && Math.abs(score)>=inThreshold && score*lastExitScore>=0;
    }
}

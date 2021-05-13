package mars.robot.ec;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

import static mars.Constants.MINIMAL_BID;

public strictfp class Bidder {

    private final RobotController rc;

    private final int[] voteCount = new int[3001];
    private final int[] amountVoted = new int[3001];
    private int wonEnemy = 0;
    private int wonMars = 0;

    Bidder(EnlightenmentCenter ec) {
        rc = ec.rc;
        amountVoted[0] = 2;
    }

    public void bid() throws GameActionException {
        //gets status information
        final int currentVotes = rc.getTeamVotes();
        final int currentInfluence = rc.getInfluence();
        voteCount[rc.getRoundNum()] = currentVotes;

        //Make a counter for how much our team lost vs enemy team
        if (currentVotes == voteCount[rc.getRoundNum() - 1]) {
            wonEnemy += 1;
        } else {
            wonMars += 1;
        }
        //vote until you have more than half the votes
        if (rc.getTeamVotes() < 751 && rc.getInfluence() > 0 && rc.getRoundNum() > 100) {
            if ((wonEnemy + 5) > wonMars) {
                //checks if we have an advantage, runs if statement when no advantage
                if (voteCount[rc.getRoundNum()] == voteCount[rc.getRoundNum() - 1]) {
                    //if the previous round was lost, bid one more as last time
                    //or vote half this or three, depending on current influence
                    int wantBid = amountVoted[rc.getRoundNum() - 1] + 1;
                    if (rc.canBid(wantBid)) {
                        amountVoted[rc.getRoundNum()] = wantBid;
                        rc.bid(wantBid);
                    } else {
                        //tries to vote half, 3 or current influence (whatever is the highest possible)
                        int wantVote = (int) Math.round(currentInfluence / 2.);
                        if (rc.canBid(wantVote)) {
                            amountVoted[rc.getRoundNum()] = wantVote;
                            rc.bid(wantVote);
                        } else if (rc.canBid(MINIMAL_BID)) {
                            amountVoted[rc.getRoundNum()] = MINIMAL_BID;
                            rc.bid(MINIMAL_BID);
                        } else {
                            int toBid = rc.getInfluence();
                            amountVoted[rc.getRoundNum()] = toBid;
                            rc.bid(toBid);
                        }

                    }
                } else {
                    //if won the previous round, either bid the same as last round,three or current influence
                    int wantBid = amountVoted[rc.getRoundNum() - 1];
                    if (rc.canBid(wantBid)) {
                        amountVoted[rc.getRoundNum()] = wantBid;
                        rc.bid(wantBid);
                    } else if (rc.canBid(MINIMAL_BID)) {
                        amountVoted[rc.getRoundNum()] = MINIMAL_BID;
                        rc.bid(MINIMAL_BID);
                    } else {
                        int toBid = rc.getInfluence();
                        amountVoted[rc.getRoundNum()] = toBid;
                        rc.bid(toBid);
                    }
                }
            } else {
                //with an advantage of votes, just bid 1
                if (rc.canBid(MINIMAL_BID)) {
                    amountVoted[rc.getRoundNum()] = MINIMAL_BID;
                    rc.bid(MINIMAL_BID);
                } else {
                    int toBid = rc.getInfluence();
                    amountVoted[rc.getRoundNum()] = toBid;
                    rc.bid(toBid);
                }
            }
        }
    }

}

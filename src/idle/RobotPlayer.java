package idle;

import battlecode.common.Clock;
import battlecode.common.RobotController;

@SuppressWarnings("unused")
public strictfp class RobotPlayer {

    public static void run(RobotController rc) {
        //noinspection InfiniteLoopStatement
        while (true) Clock.yield();
    }

}

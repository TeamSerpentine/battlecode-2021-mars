package mars;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import mars.robot.Muckraker;
import mars.robot.Politician;
import mars.robot.Slanderer;
import mars.robot.ec.EnlightenmentCenter;

@SuppressWarnings("unused")
public final strictfp class RobotPlayer {

    public static void run(final RobotController rc) {
        try {
            switch (rc.getType()) {
                case ENLIGHTENMENT_CENTER:
                    new EnlightenmentCenter(rc).loop();
                    break;
                case MUCKRAKER:
                    new Muckraker(rc).loop();
                    break;
                case POLITICIAN:
                    new Politician(rc).loop();
                    break;
                case SLANDERER:
                    new Slanderer(rc).loop();
                    break;
            }
        } catch (GameActionException e) {
            System.out.println("Exception from " + rc.getType() + ": " + e.getLocalizedMessage());
        }
    }

}

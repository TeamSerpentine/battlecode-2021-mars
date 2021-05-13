package mars.robot;

import battlecode.common.*;

import java.util.Random;

import static mars.Constants.FLAG_MASK;

public abstract strictfp class Robot {

    /**
     * All directions except CENTER.
     */
    public static final Direction[] DIRECTIONS = new Direction[]{
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    /**
     * A set of index offsets which can be used to iterate over directions in order of closeness to a given direction.
     */
    public static final int[] DIRECTION_OFFSETS = new int[]{0, 7, 1, 6, 2, 5, 3, 4};

    /**
     * All directions including CENTER.
     */
    public static final Direction[] ALL_DIRECTIONS = new Direction[]{
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
            Direction.CENTER
    };
    public final RobotController rc;
    protected final Random random = new Random();

    protected Robot(final RobotController rc) {
        this.rc = rc;
    }

    /**
     * Encodes [coordinate] into the rightmost 7 bits of a message.
     */
    public static int encodeCoordinate(int coordinate) {
        return coordinate % 128;
    }

    /**
     * Encodes [location] into the rightmost 14 bits of a message.
     */
    public static int encodeLocation(MapLocation location) {
        return 128 * (location.x % 128) + (location.y % 128);
    }

    /**
     * Decodes the rightmost 7 bits of [flag] to a coordinate, given a comparison coordinate value of this robot.
     */
    public static int decodeCoordinate(int message, int thisCoordinate) {
        int flagCoordinate = message % 128;
        int dc = (flagCoordinate - thisCoordinate) % 128 + 128;
        if (dc >= 64) dc -= 128;
        return dc + thisCoordinate;
    }

    /**
     * Returns {@code true} if {@code a} is strictly closer than {@code b}.
     */
    protected final boolean isCloser(final MapLocation a, final MapLocation b) {
        final MapLocation location = rc.getLocation();
        return location.distanceSquaredTo(a) < location.distanceSquaredTo(b);
    }

    public final void loop() throws GameActionException {
        if (this instanceof Unit)
            ((Unit) this).init();
        while (true) {
            try {
                // check for conversions
                if (this instanceof Slanderer && rc.getType() == RobotType.POLITICIAN) {
                    new Politician((Slanderer) this).loop();
                    return;
                }
                // read off the flag of our EC
                if (this instanceof Unit)
                    ((Unit) this).readSpawnFlag();

                step();
                updateFlag();
            } catch (GameActionException e) {
                System.out.println("Exception from " + rc.getType() + ": " + e.getLocalizedMessage());
            }
            Clock.yield();
        }
    }

    protected abstract void step() throws GameActionException;

    protected RobotInfo senseLoc(MapLocation senseThisLoc) throws GameActionException {
        if (senseThisLoc != null) {
            if (rc.canSenseLocation(senseThisLoc)) {
                return rc.senseRobotAtLocation(senseThisLoc);
            }
        }
        return null;
    }

    /**
     * Checks if there is a out of map square which this robot can sense.
     *
     * @return MapLocation of the border if one is found, null otherwise.
     * If a corner could be inferred the corner location is returned.
     * The returned location is on the map.
     */
    protected MapLocation senseBorder() throws GameActionException {
        MapLocation ownLocation = rc.getLocation();
        int maxRadius = (int) Math.sqrt(rc.getType().sensorRadiusSquared);

        MapLocation borderXpos = ownLocation.translate(maxRadius, 0);
        MapLocation borderXneg = ownLocation.translate(-maxRadius, 0);

        // Check for a border above and below
        int xPos = ownLocation.x;
        if (!rc.onTheMap(borderXpos)) {
            for (int radius = maxRadius - 1; radius > 0; radius--) {
                if (rc.onTheMap(ownLocation.translate(radius, 0))) {
                    xPos = ownLocation.x + radius;
                    break;
                }
            }
        } else if (!rc.onTheMap(borderXneg)) {
            for (int radius = maxRadius - 1; radius > 0; radius--) {
                if (rc.onTheMap(ownLocation.translate(-radius, 0))) {
                    xPos = ownLocation.x - radius;
                    break;
                }
            }
        }

        MapLocation borderYpos = ownLocation.translate(0, maxRadius);
        MapLocation borderYneg = ownLocation.translate(0, -maxRadius);

        int yPos = ownLocation.y;
        if (!rc.onTheMap(borderYpos)) {
            for (int radius = maxRadius - 1; radius > 0; radius--) {
                if (rc.onTheMap(ownLocation.translate(0, radius))) {
                    yPos = ownLocation.y + radius;
                    break;
                }
            }
        } else if (!rc.onTheMap(borderYneg)) {
            for (int radius = maxRadius - 1; radius > 0; radius--) {
                if (rc.onTheMap(ownLocation.translate(0, -radius))) {
                    yPos = ownLocation.y - radius;
                    break;
                }
            }
        }

        if (xPos != ownLocation.x || yPos != ownLocation.y) {
            return new MapLocation(xPos, yPos);
        }
        return null;
    }

    protected abstract void updateFlag() throws GameActionException;

    /**
     * Sets the flag of this robot and encodes it.
     */
    public final void setFlagEncoded(int message) throws GameActionException {
        rc.setFlag(FLAG_MASK & (~message));
    }

    /**
     * Returns the decoded flag of a robot with [id].
     * Assumes rc.canGetFlag(id) to be true.
     */
    public final int getFlagDecoded(int id) throws GameActionException {
        return FLAG_MASK & (~rc.getFlag(id));
    }

    /**
     * Decodes the rightmost 14 bits of [message] to a MapLocation.
     */
    public MapLocation decodeLocation(int message) {
        int flagX = (message / 128) % 128;
        int flagY = message % 128;
        int thisX = rc.getLocation().x;
        int thisY = rc.getLocation().y;
        int dx = (flagX - thisX) % 128 + 128;
        int dy = (flagY - thisY) % 128 + 128;
        if (dx >= 64) dx -= 128;
        if (dy >= 64) dy -= 128;
        return new MapLocation(dx + thisX, dy + thisY);
    }
}

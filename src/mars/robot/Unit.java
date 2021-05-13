package mars.robot;

import battlecode.common.*;
import mars.util.RandomIntSet8;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static mars.Constants.F_B_EC_SCAN;
import static mars.Constants.LOST_SPAWN;

public abstract strictfp class Unit extends Robot {

    private final RandomIntSet8 randomSet = new RandomIntSet8();
    /**
     * Collection of flags added by queueSpawnMessage(). Should have a structure of
     * [-1, -1, m1, m2, ..., m3, -1] where the first two -1-s are optional. The null values are used
     * for initial synchronization with the spawn scan cycle.
     */
    private final Deque<Integer> spawnMessages = new ArrayDeque<>(Arrays.asList(-1, -1));
    protected MapLocation spawnLocation;
    protected int spawnId = LOST_SPAWN;
    /**
     * Latest message from our spawn or [LOST_SPAWN] if the spawnId of this unit is unknown, because
     * of either politician conversion of due to losing our EC to the opponents
     */
    protected int spawnMessage = LOST_SPAWN;
    /**
     * Last seen scan flag bit of the spawn EC
     */
    protected int spawnScanFlagBit;

    protected Unit(RobotController rc) {
        super(rc);
    }

    /**
     * Looks for spawn adjacent to robot
     */
    void init() throws GameActionException {
        // try to find an EC that spawned this unit
        for (RobotInfo robot : rc.senseNearbyRobots(2, rc.getTeam())) {
            if (robot.type == RobotType.ENLIGHTENMENT_CENTER) {
                setSpawn(robot.location, robot.ID);
                readSpawnFlag();
                break;
            }
        }
    }

    /**
     * Reads the flag of the units spawn and checks whether it is still on our team.
     */
    void readSpawnFlag() throws GameActionException {
        if (spawnId == LOST_SPAWN)
            return;
        if (rc.canGetFlag(spawnId)) {
            spawnMessage = getFlagDecoded(spawnId);
        } else {
            spawnLocation = null;
            spawnId = spawnMessage = LOST_SPAWN;
        }
    }

    /**
     * Listener for when the spawn EC of this unit is either:
     * Reconverted to our team after being converted to the enemy team,
     * or initialized by setSpawn.
     */
    protected void onNewSpawn() {
    }

    /**
     * Returns a flag message to present when there are no messages scheduled for the EC. By default it returns 0.
     */
    protected int getAlternativeFlagMessage() {
        return 0;
    }

    @Override
    protected void updateFlag() throws GameActionException {
        if (!setFlagForSpawnCommunication())
            setFlagEncoded(getAlternativeFlagMessage());
    }

    /**
     * Sets the spawn information of this unit.
     */
    protected final void setSpawn(MapLocation location, int id) throws GameActionException {
        spawnLocation = location;
        spawnId = id;
        spawnScanFlagBit = -1;
        spawnMessages.clear();
        spawnMessages.add(-1);
        spawnMessages.add(-1);
        if (id != LOST_SPAWN)
            onNewSpawn();
    }

    /**
     * Adds a message to the queue of messages that are to be send to our spawn. Does not do anything when we do not know spawn.
     */
    protected final void queueSpawnMessage(int message) {
        if (spawnMessage != LOST_SPAWN) {
            // if queue is longer than [null], the last element of the queue is a removable null

            // [-1, (-1), m1, m2, ..., -1] -> [-1, (-1), m1, m2, ..., message, -1]
            // [m1, m2, ..., -1] -> [m1, m2, ..., message, -1]
            // [-1, (-1)] -> [-1, (-1), message, -1]

            // remove a -1 except if queue is [-1] or [-1, -1]
            if (spawnMessages.size() != 1 && !(spawnMessages.size() == 2 && spawnMessages.getFirst() == -1))
                spawnMessages.removeLast();
            spawnMessages.addLast(message);
            spawnMessages.addLast(-1);
        }
    }

    /**
     * If applicable, sets the flag of this unit to a scheduled flag that communicates info to spawn.
     * Returns true if the flag was set due to this process or false if the flag is not set yet.
     * This method should be called every step at the end of the step() method.
     * If our spawn is no longer on our team, or we have no known spawn, this will reset the scheduled flags and return false.
     */
    private boolean setFlagForSpawnCommunication() throws GameActionException {
        if (spawnMessage == LOST_SPAWN) {
            spawnMessages.clear();
            spawnMessages.add(-1);
            spawnMessages.add(-1);
            return false;
        }

        int newSpawnScanFlagBit = (spawnMessage & F_B_EC_SCAN) == 0 ? 0 : 1;

        // check and set the spawn scan flag bit for newly set ECs
        if (spawnScanFlagBit == -1) {
            spawnScanFlagBit = newSpawnScanFlagBit;
            return false;
        }

        // if scan cycle bit has changed, move to next item in the queue
        if (newSpawnScanFlagBit != spawnScanFlagBit) {
            spawnScanFlagBit = newSpawnScanFlagBit;
            // if queue is [-1], do not remove this element
            if (spawnMessages.size() > 1)
                spawnMessages.removeFirst();
        }

        // send message if not -1
        int message = spawnMessages.getFirst();
        if (message == -1)
            return false;
        setFlagEncoded(message);
        return true;
    }

    protected boolean randomMove() throws GameActionException {
        randomSet.reset();
        while (!randomSet.isEmpty())
            if (tryMove(DIRECTIONS[randomSet.pollRandom()]))
                return true;
        return false;
    }

    protected boolean tryMove(final Direction direction) throws GameActionException {
        if (direction == Direction.CENTER) return true;
        if (rc.canMove(direction)) {
            rc.move(direction);
            return true;
        }
        return false;
    }

    protected boolean tryMove(final MapLocation location) throws GameActionException {
        return tryMove(rc.getLocation().directionTo(location));
    }

    protected boolean tryMovePreferred(final Direction direction) throws GameActionException {
        if (direction == Direction.CENTER) return true;
        final int i = direction.ordinal();
        for (int j = 0; j < 3; j++) {
            final Direction d = DIRECTIONS[(i + DIRECTION_OFFSETS[j]) % 8];
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false;
    }

    protected boolean tryMovePreferred(final MapLocation location) throws GameActionException {
        return tryMovePreferred(rc.getLocation().directionTo(location));
    }

    /**
     * Senses all enemy robots in range.
     *
     * @return An array of enemies.
     */
    protected RobotInfo[] scanEnemy() {
        return this.rc.senseNearbyRobots(-1, rc.getTeam().opponent());
    }

}

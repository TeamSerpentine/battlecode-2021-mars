package mars.robot;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

import java.util.Arrays;

import static battlecode.common.RobotType.*;
import static mars.Constants.*;

public strictfp class Politician extends Unit {

    private final Type type;
    /**
     * Max conviction of all enemy units within some distance squared. Member instead of local variable so it doesn't
     * have to be recreated every step. Value at index 0 not used.
     */
    private final int[] maxEnemyConviction = new int[POLITICIAN.actionRadiusSquared + 1];
    /**
     * Number of units (both teams!) within some distance squared. Member instead of local variable so it doesn't have
     * to be recreated every step. Value at index 0 not used.
     */
    private final int[] unitCount = new int[POLITICIAN.actionRadiusSquared + 1];
    /**
     * Number of consecutive steps that we wanted to move and were ready, but couldn't.
     */
    private int failedMoves = 0;
    /**
     * Target assigned by spawn EC. If {@code null}, there is not explicit target.
     */
    private MapLocation target = null;
    /**
     * Since the sensor radius is greater than the action radius, we can see Muckrakers before we can kill them in
     * {@code tryEmpower()}. Therefore, we keep track of the closest one as a secondary target. Set to {@code null} at
     * the start of every step.
     */
    private RobotInfo targetMuckraker;

    public Politician(final RobotController rc) throws GameActionException {
        super(rc);
        type = spawnMessage == LOST_SPAWN ? Type.OFFENSIVE : Type.fromFlag(spawnMessage);
    }

    public Politician(final Slanderer slanderer) throws GameActionException {
        super(slanderer.rc);
        setSpawn(slanderer.spawnLocation, slanderer.spawnId);
        type = rc.getRoundNum() > 500 && Math.random() < 0.5
                ? Type.OFFENSIVE : Type.DEFENSIVE;
    }

    private boolean isStuck() {
        return failedMoves >= 3;
    }

    private void scanUnits() throws GameActionException {
        Arrays.fill(maxEnemyConviction, 0);
        Arrays.fill(unitCount, 0);
        targetMuckraker = null;

        // we assume it takes 10 turns to reach slanderer
        final int effectiveConviction = (int) (rc.getEmpowerFactor(rc.getTeam(), 10) * (rc.getConviction() - 10));

        for (final RobotInfo r : rc.senseNearbyRobots()) {
            final int distance = rc.getLocation().distanceSquaredTo(r.location);
            if (distance <= POLITICIAN.actionRadiusSquared)
                unitCount[distance]++;

            if (r.team == rc.getTeam()) {
                if (spawnLocation == null) {
                    // if we don't have a spawn ..

                    if (r.type == ENLIGHTENMENT_CENTER) {
                        // .. and come across a friendly EC, adopt it
                        setSpawn(r.location, r.ID);
                        continue;
                    }

                    final int message = getFlagDecoded(r.ID);
                    final int action = message & F_M_ACTION;

                    if (action == F_V_COMMUNICATE_SPAWN && spawnMessage == LOST_SPAWN) {
                        // .. and come across a friend that is offering one, adopt it
                        setSpawn(null, message & F_M_ID);
                    }
                }
            } else if (r.team == rc.getTeam().opponent()) {
                if (distance <= POLITICIAN.actionRadiusSquared) {
                    // enemy politicians only need to be neutralized, which means <=10 conviction
                    maxEnemyConviction[distance] =
                            Math.max(r.conviction - (r.type == POLITICIAN ? 10 : 0), maxEnemyConviction[distance]);
                }

                // we require having 2x as much conviction, as a buffer (other units could be next to it, for example)
                if (r.type == MUCKRAKER && r.conviction * 2 < effectiveConviction &&
                        (targetMuckraker == null || isCloser(r.location, targetMuckraker.location))) {
                    targetMuckraker = r;
                }
            }
        }
    }

    /**
     * If stuck or defensive or offensive & within range of target, determine an effective range to empower on (i.e. not
     * wasteful and enemy units in it will be affected) and, if found, empower!
     */
    private boolean tryEmpower() throws GameActionException {
        if (type == Type.DEFENSIVE || isStuck() || (target != null &&
                rc.getLocation().isWithinDistanceSquared(target, POLITICIAN.actionRadiusSquared))) {
            final int effectiveConviction = (int) (rc.getEmpowerFactor(rc.getTeam(), 0) * (rc.getConviction() - 10));
            for (int r = 1; r <= POLITICIAN.actionRadiusSquared; r++) {
                // ugly, but it works
                if (target != null && type == Type.OFFENSIVE && r < 5 &&
                        rc.getLocation().isWithinDistanceSquared(target, r)) {
                    rc.empower(r);
                    return true;
                }

                final int requiredConviction = maxEnemyConviction[r] * unitCount[r];
                if (requiredConviction == 0) continue;
                if (requiredConviction > effectiveConviction) break;
                if (requiredConviction * 5 < effectiveConviction)
                    continue; // not worth it

                rc.empower(r);
                return true;
            }
        }
        return false;
    }

    /**
     * Move towards target if one is specified, or else randomly.
     */
    private void moveToTarget() throws GameActionException {
        boolean success;

        if (target == null) {
            if (targetMuckraker == null || type == Type.OFFENSIVE) {
                if (spawnLocation == null || rc.getLocation().isWithinDistanceSquared(spawnLocation, 48)) {
                    success = randomMove();
                } else {
                    success = tryMovePreferred(spawnLocation);
                }
            } else {
                success = tryMovePreferred(targetMuckraker.location);
            }
        } else {
            success = tryMovePreferred(target);
        }

        failedMoves = success ? 0 : failedMoves + 1;
    }

    @Override
    public void step() throws GameActionException {
        // green if defensive, red if offensive
        rc.setIndicatorDot(rc.getLocation(), type == Type.OFFENSIVE ? 255 : 0, type == Type.DEFENSIVE ? 255 : 0, 0);

        scanUnits();

        // is EC giving new orders?
        if (spawnMessage != LOST_SPAWN && (spawnMessage & F_M_ACTION) == F_V_EC_ATTACK && Type.fromFlag(spawnMessage) == type) {
            // orders to defend/attack spawn are ignored, since this is default behavior
            final MapLocation location = decodeLocation(spawnMessage);
            target = location.equals(spawnLocation) ? null : location;
        }

        // if we cannot move/empower there is not point in continuing
        if (!rc.isReady()) return;

        // calculate 'cumulative sums'
        for (int r = 2; r < maxEnemyConviction.length; r++) {
            maxEnemyConviction[r] = Math.max(maxEnemyConviction[r - 1], maxEnemyConviction[r]);
            unitCount[r] += unitCount[r - 1];
        }

        if (tryEmpower()) return;
        moveToTarget();
    }

    @Override
    protected int getAlternativeFlagMessage() {
        if (spawnMessage == LOST_SPAWN) return F_V_POLITICIAN_LOST;
        return spawnId < F_M_ID ? (F_V_COMMUNICATE_SPAWN | spawnId) : 0;
    }

    /**
     * Represents a team of politicians that share specific tasks.
     */
    public strictfp enum Type {

        DEFENSIVE(F_V_POLITICIAN_TYPE_DEFENSIVE),
        OFFENSIVE(F_V_POLITICIAN_TYPE_OFFENSIVE);

        public static final Type[] VALUES = Type.values();

        /**
         * The bits used to indicate this type on a flag.
         */
        public final int spawnInstruction;

        Type(int spawnInstruction) {
            this.spawnInstruction = spawnInstruction;
        }

        /**
         * Returns the type that is encoded on the flag.
         */
        public static Type fromFlag(final int flag) {
            return (flag & F_M_POLITICIAN_TYPE) == F_V_POLITICIAN_TYPE_DEFENSIVE ? DEFENSIVE : OFFENSIVE;
        }

    }

}

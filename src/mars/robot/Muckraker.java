package mars.robot;

import battlecode.common.*;

import java.util.*;

import static battlecode.common.RobotType.*;
import static mars.Constants.*;

public strictfp class Muckraker extends Unit {

    private final Map<MapLocation, Integer> communicatedECs = new HashMap<>();
    // inclusive! i.e. lies inside map
    private int borderTop = -1;
    private int borderRight = -1;
    private int borderBottom = -1;
    private int borderLeft = -1;

    public Muckraker(final RobotController rc) throws GameActionException {
        super(rc);
    }

    /**
     * Tries to sense border, and stores result & transfers it to Center via flag if found. Also adds all borders
     * (sensed or stored) to {@code repellers}.
     */
    private void senseBorders(final Collection<MapLocation> repellers) throws GameActionException {
        final MapLocation location = rc.getLocation();
        final int x = location.x;
        final int y = location.y;

        // we use the constant 5 == floor(sqrt(30)), where 30 is the Muckraker's sensor radius
        senseBordersHorizontal(repellers, x, y);
        senseBordersVertical(repellers, x, y);
    }

    private void senseBordersHorizontal(final Collection<MapLocation> repellers, final int x, final int y)
            throws GameActionException {
        if (borderRight == -1) {
            MapLocation location = new MapLocation(x + 5, y);
            if (!rc.onTheMap(location)) {
                do {
                    location = location.add(Direction.WEST);
                } while (location.x != x && !rc.onTheMap(location));
                repellers.add(location.subtract(Direction.WEST));
                borderRight = location.x;
                queueSpawnMessage(F_V_NEW_BORDER | encodeCoordinate(borderRight));

                if (borderLeft != -1)
                    repellers.add(new MapLocation(borderLeft - 1, y));
                return; // the left border is guaranteed to be outside sensor range
            }
        } else {
            repellers.add(new MapLocation(borderRight + 1, y));
        }

        if (borderLeft == -1) {
            MapLocation location = new MapLocation(x - 5, y);
            if (!rc.onTheMap(location)) {
                do {
                    location = location.add(Direction.EAST);
                } while (location.x != x && !rc.onTheMap(location));
                repellers.add(location.subtract(Direction.EAST));
                borderLeft = location.x;
                queueSpawnMessage(F_V_NEW_BORDER | encodeCoordinate(borderLeft));
            }
        } else {
            repellers.add(new MapLocation(borderLeft - 1, y));
        }
    }

    private void senseBordersVertical(final Collection<MapLocation> repellers, final int x, final int y)
            throws GameActionException {
        if (borderTop == -1) {
            MapLocation location = new MapLocation(x, y + 5);
            if (!rc.onTheMap(location)) {
                do {
                    location = location.add(Direction.SOUTH);
                } while (location.y != y && !rc.onTheMap(location));
                repellers.add(location.subtract(Direction.SOUTH));
                borderTop = location.y;
                queueSpawnMessage(F_V_NEW_BORDER | F_B_COORDINATE_TYPE | encodeCoordinate(borderTop));

                if (borderBottom != -1)
                    repellers.add(new MapLocation(x, borderBottom - 1));
                return; // the bottom border is guaranteed to be outside sensor range
            }
        } else {
            repellers.add(new MapLocation(x, borderTop + 1));
        }

        if (borderBottom == -1) {
            MapLocation location = new MapLocation(x, y - 5);
            if (!rc.onTheMap(location)) {
                do {
                    location = location.add(Direction.NORTH);
                } while (location.y != y && !rc.onTheMap(location));
                repellers.add(location.subtract(Direction.NORTH));
                borderBottom = location.y;
                queueSpawnMessage(F_V_NEW_BORDER | F_B_COORDINATE_TYPE | encodeCoordinate(borderBottom));
            }
        } else {
            repellers.add(new MapLocation(x, borderBottom - 1));
        }
    }

    /**
     * Calculates direction where the least number of repellers are located, and tries to move towards it.
     */
    private void spreadOut(final Iterable<MapLocation> repellers) throws GameActionException {
        final float[] score = new float[ALL_DIRECTIONS.length];
        for (int i = 0; i < score.length; i++) {
            final MapLocation location = rc.getLocation().add(ALL_DIRECTIONS[i]);
            // distanceSquaredTo(location) might be 0, and 1.0 / (int) 0 = +Inf. This is not a problem, however, since
            // we find the minimum, which filters out +Inf.
            for (final MapLocation r : repellers)
                score[i] += 1.0 / r.distanceSquaredTo(location);
        }

        int best = 0;
        for (int i = 1; i < score.length; i++) // skips i = 0
            if (score[i] < score[best])
                best = i;

        tryMove(ALL_DIRECTIONS[best]);
    }

    @Override
    protected void step() throws GameActionException {
        RobotInfo bestSlanderer = null;
        final List<MapLocation> repellers = new ArrayList<>();

        // 1. Find non-friendly Centers (& set flag if found), find closest & most valuable Slanderer, and scan for
        //    friendly Centers and Muckrakers that will act as repellers.
        for (final RobotInfo r : rc.senseNearbyRobots()) {
            if (r.team == rc.getTeam()) {
                if (r.type == ENLIGHTENMENT_CENTER || r.type == MUCKRAKER)
                    repellers.add(r.location);
            } else {
                if (r.type == SLANDERER) {
                    // Ideally, you'd want to target the youngest slanderer <50 turns old first, or else the oldest >=50
                    // turns. However, their age cannot be accurately determined, so we just target the closest one.
                    if (bestSlanderer == null || r.influence > bestSlanderer.influence ||
                            (r.influence == bestSlanderer.influence && isCloser(r.location, bestSlanderer.location))) {
                        bestSlanderer = r;
                    }
                }
            }

            if (r.type == ENLIGHTENMENT_CENTER && !r.location.equals(spawnLocation)) {
                // communicate the location and id of this found EC
                Integer id = r.team == rc.getTeam() ? r.ID : null;
                if (!communicatedECs.containsKey(r.location) || !Objects.equals(communicatedECs.get(r.location), id)) {
                    communicatedECs.put(r.location, id);
                    queueSpawnMessage(F_V_NEW_EC_COORDINATES | encodeLocation(r.location));
                    if (id != null && r.ID <= F_M_ID)
                        queueSpawnMessage(F_V_NEW_EC_ID | r.ID);
                }
            }
        }

        // 2. Kill closest & most valuable Slanderer (if we found one).
        if (bestSlanderer != null && tryExpose(bestSlanderer.location)) return;

        // 3. Sense borders (or read from cache) to add as repellers and send to Center.
        senseBorders(repellers);

        // 4. Move to least 'crowded' area.
        spreadOut(repellers);
    }

    @Override
    protected void onNewSpawn() {
        // resend EC information
        for (Map.Entry<MapLocation, Integer> ec : communicatedECs.entrySet()) {
            queueSpawnMessage(F_V_NEW_EC_COORDINATES | encodeLocation(ec.getKey()));
            if (ec.getValue() != null && ec.getValue() <= F_M_ID)
                queueSpawnMessage(F_V_NEW_EC_ID | ec.getValue());
        }

        // resend border information
        if (borderRight != -1)
            queueSpawnMessage(F_V_NEW_BORDER | encodeCoordinate(borderRight));
        if (borderLeft != -1)
            queueSpawnMessage(F_V_NEW_BORDER | encodeCoordinate(borderLeft));
        if (borderTop != -1)
            queueSpawnMessage(F_V_NEW_BORDER | F_B_COORDINATE_TYPE | encodeCoordinate(borderTop));
        if (borderBottom != -1)
            queueSpawnMessage(F_V_NEW_BORDER | F_B_COORDINATE_TYPE | encodeCoordinate(borderBottom));
    }

    /**
     * Tries to expose the Slanderer at the specified location.
     *
     * @return {@code true} if we're done for this turn (i.e. we exposed the slanderer or moved towards it, or we're
     * waiting for the next turn)
     */
    private boolean tryExpose(final MapLocation slanderer) throws GameActionException {
        if (rc.getLocation().isWithinDistanceSquared(slanderer, MUCKRAKER.actionRadiusSquared)) {
            // canExpose not necessary, since after isReady all requirements are met
            if (rc.isReady()) rc.expose(slanderer);
            return true;
        } else {
            return tryMove(slanderer);
        }
    }

}

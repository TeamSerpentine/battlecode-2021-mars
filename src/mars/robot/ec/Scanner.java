package mars.robot.ec;

import battlecode.common.*;
import mars.robot.Politician;
import mars.robot.Robot;

import java.util.*;

import static mars.Constants.*;
import static mars.robot.Robot.encodeCoordinate;

public strictfp class Scanner {

    // map of known EC locations to their IDs, the ID might be null, which indicates that their ID is not known yet or it is an enemy EC or it is this EC.
    final Map<MapLocation, Integer> ecs = new HashMap<>();
    final List<Integer> borderCodes = new ArrayList<>();
    private final EnlightenmentCenter ec;
    private final RobotController rc;
    // list of UnitInfo of units that are spawned by this EC and have not died or been converted yet.
    private final List<UnitInfo> units = new LinkedList<>();
    private final List<UnitInfo> newUnits = new ArrayList<>();
    int lowX, lowY, uppX, uppY;
    private final Symmetry[] symmetries = new Symmetry[]{
            // vertical symmetry
            loc -> new MapLocation(loc.x, uppY - (loc.y - lowY)),
            // horizontal symmetry
            loc -> new MapLocation(uppX - (loc.x - lowX), loc.y),
            // rotational symmetry
            loc -> new MapLocation(uppX - (loc.x - lowX), uppY - (loc.y - lowY))
    };
    boolean scanParityBit;
    // fields of nearby scan
    int nearbyFriendlyMuckrakers;
    int nearbyEnemyMuckrakers;
    int strongestEnemyMuckraker;
    int nearbyEnemySlanderers;
    int strongestEnemySlanderer;
    MapLocation nearestEnemyLocation;
    // fields of units scan
    int offensivePoliticianPower;
    int defensivePoliticianPower;
    int strongestDefensivePolitician;
    int slandererCount;

    // usable variables
    int slandererIncome;
    int muckrakerCount;
    MapLocation panicSlanderer;
    // the map is within lowX, lowY (inclusive) and uppX, uppY (inclusive)
    // 0 values indicate unknown values
    private Symmetry symmetry;
    // variables of current scan
    private Iterator<UnitInfo> unitIterator;
    private boolean scanNewECs;
    private int scanOffensivePoliticianPower;
    private int scanDefensivePoliticianPower;
    private int scanStrongestDefensivePolitician;
    private int scanSlandererCount;
    private int scanSlandererIncome;
    private int scanMuckrakerCount;
    private MapLocation scanPanicSlanderer;
    private int scanMaxPanicScore;

    Scanner(EnlightenmentCenter ec) {
        this.ec = ec;
        rc = ec.rc;
        ecs.put(rc.getLocation(), null);
    }

    void scanNearby() {
        // reset fields
        nearbyFriendlyMuckrakers = 0;
        nearbyEnemyMuckrakers = 0;
        strongestEnemyMuckraker = Integer.MIN_VALUE;
        nearbyEnemySlanderers = 0;
        strongestEnemySlanderer = Integer.MIN_VALUE;
        nearestEnemyLocation = null;

        int minD2 = Integer.MAX_VALUE;

        // scan vision range for information
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                // update state of ECs if we can manually sense them
                ecs.put(robot.location, robot.team == rc.getTeam() ? robot.ID : null);
            } else if (robot.team == rc.getTeam().opponent()) {
                // count enemy robots
                if (robot.type == RobotType.MUCKRAKER) {
                    nearbyEnemyMuckrakers++;
                    strongestEnemyMuckraker = Math.max(strongestEnemyMuckraker, robot.conviction);
                    int d2 = rc.getLocation().distanceSquaredTo(robot.location);
                    if (d2 < minD2) {
                        minD2 = d2;
                        nearestEnemyLocation = rc.getLocation();
                    }
                } else if (robot.type == RobotType.SLANDERER) {
                    nearbyEnemySlanderers++;
                    strongestEnemySlanderer = Math.max(strongestEnemySlanderer, robot.conviction);
                    int d2 = rc.getLocation().distanceSquaredTo(robot.location);
                    if (d2 < minD2) {
                        minD2 = d2;
                        nearestEnemyLocation = rc.getLocation();
                    }
                }
            } else if (robot.type == RobotType.MUCKRAKER && robot.team == rc.getTeam()) {
                nearbyFriendlyMuckrakers++;
            }
        }
    }

    void scanECs() {
        for (Map.Entry<MapLocation, Integer> otherEc : ecs.entrySet()) {
            Integer id = otherEc.getValue();
            if (id == null)
                continue;
            if (!rc.canGetFlag(id))
                otherEc.setValue(null);
        }
    }

    void scanUnits() throws GameActionException {
        // if at the begin of the scan cycle, initialize the scan and scan ECs
        if (unitIterator == null) {
            unitIterator = units.iterator();
            scanNewECs = false;

            scanOffensivePoliticianPower = 0;
            scanDefensivePoliticianPower = 0;
            scanStrongestDefensivePolitician = Integer.MIN_VALUE;
            scanSlandererCount = 0;
            scanSlandererIncome = 0;
            scanMuckrakerCount = 0;
            scanPanicSlanderer = null;
            scanMaxPanicScore = Integer.MIN_VALUE;
        }

        while (Clock.getBytecodesLeft() > 2500 && unitIterator.hasNext()) {
            UnitInfo unit = unitIterator.next();
            // checks whether unit is still alive, if not, remove it from units set
            if (rc.canGetFlag(unit.id)) {
                scanUnit(unit, ec.getFlagDecoded(unit.id));
            } else {
                unitIterator.remove();
            }
        }

        // if at the end of the scan cycle
        if (!unitIterator.hasNext()) {
            // deduce more information about the map
            if (scanNewECs && borderCodes.size() == 4 && symmetry == null) {
                // if we estimate that we do not have enough bytecodes for the symmetry computation, procrastinate this to next turn
                if (Clock.getBytecodesLeft() < 4000)
                    return;

                symmetry = deduceSymmetry();
                if (symmetry != null) {
                    // we can now compute the locations of other ECs
                    for (MapLocation ec : ecs.keySet().toArray(new MapLocation[ecs.size()]))
                        if (!ecs.containsKey(ec))
                            ecs.put(symmetry.other(ec), null);
                }
            }

            unitIterator = null;

            // add all units that have been created since last scan
            units.addAll(newUnits);

            // update all usable non scan variables
            newUnits.clear();
            offensivePoliticianPower = scanOffensivePoliticianPower;
            defensivePoliticianPower = scanDefensivePoliticianPower;
            strongestDefensivePolitician = scanStrongestDefensivePolitician;
            slandererCount = scanSlandererCount;
            slandererIncome = scanSlandererIncome;
            muckrakerCount = scanMuckrakerCount;
            panicSlanderer = scanPanicSlanderer;

            // swap scan parity bit
            scanParityBit = !scanParityBit;
        }
    }

    private void scanUnit(UnitInfo unit, int message) {
        int action = message & F_M_ACTION;
        RobotType type = unit.getType();

        if (type == RobotType.POLITICIAN) {
            int power = Math.max(unit.influence - 10, 0);
            if (unit.politicianType == Politician.Type.DEFENSIVE) {
                scanDefensivePoliticianPower += power;
                scanStrongestDefensivePolitician = Math.max(scanStrongestDefensivePolitician, power);
            } else if (unit.politicianType == Politician.Type.OFFENSIVE) {
                scanOffensivePoliticianPower += power;
            }
        } else if (type == RobotType.SLANDERER) {
            slandererCount++;
            if (unit.embezzling())
                scanSlandererIncome += slandererEmbezzle(unit.influence);
            if (action == F_V_SLANDERER_PANIC) {
                MapLocation location = ec.decodeLocation(message);
                // choose the nearest slanderer of the slanderers with the highest influence and prefer slanderers that actively generate income
                int panicScore = 2 * MAX_DISTANCE_SQUARED * unit.influence + (unit.embezzling() ? MAX_DISTANCE_SQUARED : 0) - rc.getLocation().distanceSquaredTo(location);
                if (panicScore > scanMaxPanicScore) {
                    scanMaxPanicScore = panicScore;
                    scanPanicSlanderer = location;
                }
            }
        } else if (type == RobotType.MUCKRAKER) {
            scanMuckrakerCount++;
        }
        if (action == F_V_NEW_EC_COORDINATES) {
            MapLocation location = ec.decodeLocation(message);
            unit.lastLocation = location;
            if (!ecs.containsKey(location)) {
                ecs.put(location, null);
                if (symmetry == null) {
                    scanNewECs = true;
                } else {
                    location = symmetry.other(location);
                    if (!ecs.containsKey(location))
                        ecs.put(symmetry.other(location), null);
                }
            }
        } else if (action == F_V_NEW_EC_ID) {
            if (unit.lastLocation != null)
                ecs.put(unit.lastLocation, message & F_M_ID);
        } else if (action == F_V_NEW_BORDER) {
            if (borderCodes.size() < 4) {
                if ((message & F_B_COORDINATE_TYPE) == 0) {
                    // x coordinate is being communicated
                    int c = Robot.decodeCoordinate(message, rc.getLocation().x);
                    if (c <= rc.getLocation().x) {
                        if (lowX == 0) {
                            lowX = c;
                            borderCodes.add(F_V_BORDER_L | encodeCoordinate(lowX));
                        }
                    } else {
                        if (uppX == 0) {
                            uppX = c;
                            borderCodes.add(F_V_BORDER_R | encodeCoordinate(uppX));
                        }
                    }
                } else {
                    // y coordinate is being communicated
                    int c = Robot.decodeCoordinate(message, rc.getLocation().y);
                    if (c <= rc.getLocation().y) {
                        if (lowY == 0) {
                            lowY = c;
                            borderCodes.add(F_V_BORDER_B | encodeCoordinate(lowY));
                        }
                    } else {
                        if (uppY == 0) {
                            uppY = c;
                            borderCodes.add(F_V_BORDER_T | encodeCoordinate(uppY));
                        }
                    }
                }
            }
        }
    }

    void registerUnit(RobotInfo robot, Politician.Type politicianType) {
        newUnits.add(new UnitInfo(robot, politicianType));
    }

    /**
     * Deduces the symmetry of the map if it can be found or returns null if it can not be determined.
     */
    private Symmetry deduceSymmetry() {
        // should actually be checking the owner of the ECs as well, but this is not guaranteed to be correct
        MapLocation[] ecs = this.ecs.keySet().toArray(new MapLocation[this.ecs.size()]);
        for (Symmetry symmetry : symmetries)
            for (int i = 0; i < ecs.length; i++)
                for (int j = i + 1; j < ecs.length; j++)
                    if (symmetry.other(ecs[i]).equals(ecs[j]))
                        return symmetry;
        return null;
    }

    @FunctionalInterface
    private interface Symmetry {
        MapLocation other(MapLocation loc);
    }

    /**
     * Represents units spawned by an EC that have not died or been converted yet.
     */
    private class UnitInfo {

        final int influence;
        final int id;
        final int spawnRound;
        final Politician.Type politicianType;
        RobotType type;
        MapLocation lastLocation;

        private UnitInfo(RobotInfo robot, Politician.Type politicianType) {
            type = robot.type;
            influence = robot.influence;
            id = robot.ID;
            spawnRound = rc.getRoundNum();
            // slanderer converted politicians are automatically defensive
            this.politicianType = robot.type == RobotType.SLANDERER ? Politician.Type.DEFENSIVE : politicianType;
        }

        private RobotType getType() {
            if (type == RobotType.SLANDERER && (rc.getRoundNum() - spawnRound) >= GameConstants.CAMOUFLAGE_NUM_ROUNDS)
                type = RobotType.POLITICIAN;
            return type;
        }

        private boolean embezzling() {
            return type == RobotType.SLANDERER && (rc.getRoundNum() - spawnRound) < GameConstants.EMBEZZLE_NUM_ROUNDS;
        }

    }

}

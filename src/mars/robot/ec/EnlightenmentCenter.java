package mars.robot.ec;

import battlecode.common.*;
import mars.robot.Politician;
import mars.robot.Robot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static mars.Constants.*;

public strictfp class EnlightenmentCenter extends Robot {

    private final Bidder bidder = new Bidder(this);
    private final Scanner scanner = new Scanner(this);

    // targets for politician groups. targets[type.ordinal()] is the target of politician of type type.
    private final MapLocation[] targets = new MapLocation[Politician.Type.VALUES.length];
    private final int visionTiles;
    // index of target that should be communicated this turn.
    private int targetIndex;
    // flags used for communicating politician types to newly spawned politicians
    // there are 2 fields to make sure that the setting of this flag is delayed by 1 round
    private int newInstructionFlag;
    private int instructionFlag;
    // indication of progress of sending "filler" flags: border locations for slanderers and attack locations for politicians
    // values 0 and 1 are reserved for sending border locations, values >= 2 are for politician targets
    private int flagCycle;

    public EnlightenmentCenter(RobotController rc) throws GameActionException {
        super(rc);
        Arrays.fill(targets, rc.getLocation());

        // attempt to gather information about the border
        MapLocation border = senseBorder();
        if (border != null) {
            if (border.x < rc.getLocation().x) {
                scanner.lowX = border.x;
                scanner.borderCodes.add(F_V_BORDER_L | encodeCoordinate(scanner.lowX));
            } else if (border.x > rc.getLocation().x) {
                scanner.uppX = border.x;
                scanner.borderCodes.add(F_V_BORDER_R | encodeCoordinate(scanner.uppX));
            }

            if (border.y < rc.getLocation().y) {
                scanner.lowY = border.y;
                scanner.borderCodes.add(F_V_BORDER_B | encodeCoordinate(scanner.lowY));
            } else if (border.y > rc.getLocation().y) {
                scanner.uppY = border.y;
                scanner.borderCodes.add(F_V_BORDER_T | encodeCoordinate(scanner.uppY));
            }
        }

        // compute an estimation of the amount of tiles that can be seen by this EC
        int visionTiles = 120;
        if (scanner.lowX != 0)
            visionTiles += 5 * (rc.getLocation().x - scanner.lowX) - 30;
        if (scanner.lowY != 0)
            visionTiles += 5 * (rc.getLocation().y - scanner.lowY) - 30;
        if (scanner.uppX != 0)
            visionTiles += 5 * (scanner.uppX - rc.getLocation().x) - 30;
        if (scanner.uppY != 0)
            visionTiles += 5 * (scanner.uppY - rc.getLocation().y) - 30;
        this.visionTiles = visionTiles;
    }

    @Override
    public void step() throws GameActionException {
        scanner.scanNearby();

        scanner.scanECs();

        computeTargets();

        newInstructionFlag = buildUnits();

        bidder.bid();

        scanner.scanUnits();

        if (DEBUG) {
            if (scanner.scanParityBit)
                rc.setIndicatorDot(rc.getLocation(), 0, 255, 255);
            else rc.setIndicatorDot(rc.getLocation(), 255, 255, 0);

            for (Map.Entry<MapLocation, Integer> otherEC : scanner.ecs.entrySet()) {
                if (otherEC.getKey().equals(rc.getLocation()))
                    continue;
                if (otherEC.getValue() == null) {
                    rc.setIndicatorDot(otherEC.getKey(), 255, 0, 255);
                } else {
                    rc.setIndicatorDot(otherEC.getKey(), 0, 255, 0);
                }
            }
        }
    }

    @Override
    protected void updateFlag() throws GameActionException {
        int flagScanParityBit = scanner.scanParityBit ? F_B_EC_SCAN : 0;
        if (instructionFlag > 0) {
            // instruct newly spawned units with a flag
            setFlagEncoded(flagScanParityBit | instructionFlag);
        } else {
            // use a filler flag

            if ((scanner.slandererIncome == 0 || flagCycle >= (scanner.borderCodes.size() + 1) / 2) && flagCycle < 2)
                flagCycle = 2;
            if (flagCycle < 2) {
                // send border coordinates
                int i = flagCycle * 2;
                if (scanner.borderCodes.size() == i + 1) {
                    // only 1 border left to send
                    setFlagEncoded(flagScanParityBit | F_V_EC_1_BORDER | scanner.borderCodes.get(i));
                } else {
                    // 2 borders left to send
                    setFlagEncoded(flagScanParityBit | F_V_EC_2_BORDER | (scanner.borderCodes.get(i + 1) << 9) | scanner.borderCodes.get(i));
                }
            } else {
                // if we did not have to send a keep alive message
                // cycle through our attack targets to make every politician know what they should do
                setFlagEncoded(flagScanParityBit | F_V_EC_ATTACK | Politician.Type.VALUES[targetIndex].spawnInstruction | encodeLocation(targets[targetIndex]));

                targetIndex++;
                if (targetIndex == targets.length)
                    targetIndex = 0;
            }

            flagCycle++;
            if (flagCycle >= 2 + Politician.Type.VALUES.length)
                flagCycle = 0;
        }

        instructionFlag = newInstructionFlag;
    }

    /**
     * Builds units based on the influence and information scanned.
     * Returns a flag > 0 if the flag should be changed next round to instruct newly spawned units otherwise returns a value <= 0.
     */
    private int buildUnits() throws GameActionException {
        if (!rc.isReady())
            return -1;

        // if there are nearby enemies, make politicians for this

        int strongestEnemy = scanner.strongestEnemyMuckraker;
        if (strongestEnemy >= 0 &&
                (scanner.strongestDefensivePolitician < strongestEnemy ||
                        scanner.defensivePoliticianPower < strongestEnemy * (scanner.nearbyEnemyMuckrakers + scanner.nearbyEnemySlanderers)))
            return buildUnit(RobotType.POLITICIAN, strongestEnemy + EXTRA_POLITICIAN_POWER, Politician.Type.DEFENSIVE);

        // if we do not have enough slanderer income, make slanderers

        boolean sufficientSlandererProtection = scanner.nearbyEnemyMuckrakers == 0 && (rc.getRoundNum() < UNPROTECTED_ROUNDS || scanner.defensivePoliticianPower > POLITICIAN_POWER_PER_SLANDERER * scanner.slandererCount);
        boolean needForSlanderers = scanner.slandererIncome < desiredSlandererIncome();

        if (sufficientSlandererProtection && needForSlanderers) {
            int i = Arrays.binarySearch(SLANDERER_INFLUENCES, rc.getInfluence());
            if (i < 0) i = -i - 2;
            if (i >= 0)
                // if i < 0, then slanderers are too expensive, i points at the largest element of SLANDERER_INFLUENCES that is <= rc.getInfluence()
                buildUnit(RobotType.SLANDERER, SLANDERER_INFLUENCES[i], null);
            return -1;
        }

        // if we want to make slanderers, but do not have enough protection, make protection
        if (!sufficientSlandererProtection && needForSlanderers)
            return buildUnit(RobotType.POLITICIAN, defaultDefensivePoliticianInfluence(), Politician.Type.DEFENSIVE);

        // if we do not have enough offensive capabilities and have attack targets, make attackers
        if (!targets[Politician.Type.OFFENSIVE.ordinal()].equals(rc.getLocation()) && scanner.offensivePoliticianPower < defaultOffensivePowerRequirement())
            return buildUnit(RobotType.POLITICIAN, defaultOffensivePoliticianInfluence(), Politician.Type.OFFENSIVE);

        // make muckrakers if the borders of the map are not yet known
        if (scanner.borderCodes.size() < 4)
            return buildUnit(RobotType.MUCKRAKER, 1, null);

        int defaultOverflowPoliticianInfluence = defaultOverflowPoliticianInfluence();

        float localMuckrakerDensity = ((float) scanner.nearbyFriendlyMuckrakers) / visionTiles;
        float mapMuckrakerDensity = ((float) scanner.muckrakerCount) / ((scanner.uppX - scanner.lowX - 1) * (scanner.uppY - scanner.lowY - 1));

        // backup influence sinks
        if (rc.getInfluence() > 2 * defaultOverflowPoliticianInfluence || localMuckrakerDensity > LOCAL_MUCKRAKER_DENSITY || mapMuckrakerDensity > MAP_MUCKRAKER_DENSITY) {
            // if we have a lot of influence or have enough map covering, make attackers
            return buildUnit(RobotType.POLITICIAN, defaultOverflowPoliticianInfluence, Politician.Type.OFFENSIVE);
        } else {
            // otherwise make a default muckraker if the map covering is not dense enough
            return buildUnit(RobotType.MUCKRAKER, 1, null);
        }
    }

    private int desiredSlandererIncome() {
        return ((int) Math.sqrt(rc.getRoundNum()) / 2) + 8;
    }

    private int defaultDefensivePoliticianInfluence() {
        return 10 * ((int) Math.sqrt(rc.getRoundNum())) + 80;
    }

    private int defaultOffensivePoliticianInfluence() {
        return 20 * ((int) Math.sqrt(rc.getRoundNum())) + 160;
    }

    private int defaultOffensivePowerRequirement() {
        return 200 * ((int) Math.sqrt(rc.getRoundNum())) + 1600;
    }

    private int defaultOverflowPoliticianInfluence() {
        return 20 * ((int) Math.sqrt(rc.getRoundNum())) + 4 * scanner.slandererIncome + 200;
    }

    /**
     * Tries to build a robot of [type] with [influence] that is destined to do something at [objective].
     * [objective] can be null if there is no predetermined objective location.
     * Adds the robot to newUnits if successfully spawned. Also updates estimates of the scan variables.
     *
     * @return -1 if no unit could be spawned, 0 if a unit was spawned with politicianType null and a flag that should be used to instruct the spawned unit otherwise.
     */
    private int buildUnit(RobotType type, int influence, Politician.Type politicianType) throws GameActionException {
        MapLocation objective = rc.getLocation();
        if (politicianType != null)
            objective = targets[politicianType.ordinal()];

        // location where the new robot is spawned
        if (objective == rc.getLocation()) {
            // finds the valid adjacent location with maximum passability
            double maxPassability = 0;
            List<Direction> bestDirections = new ArrayList<>();
            for (Direction d : DIRECTIONS) {
                if (rc.canBuildRobot(type, d, influence)) {
                    double passability = rc.sensePassability(rc.adjacentLocation(d));
                    if (passability > maxPassability) {
                        maxPassability = passability;
                        bestDirections.clear();
                    }
                    if (passability == maxPassability)
                        bestDirections.add(d);
                }
            }

            // return false if no valid build locations
            if (bestDirections.isEmpty())
                return -1;

            // build robot and add to units set
            Direction d = bestDirections.get(random.nextInt(bestDirections.size()));
            rc.buildRobot(type, d, influence);
            scanner.registerUnit(rc.senseRobotAtLocation(rc.adjacentLocation(d)), politicianType);

            return politicianType == null ? 0 : (F_V_EC_SPAWN_POLITICIAN | politicianType.spawnInstruction);
        } else {
            // try directions closer to objective first
            int i = rc.getLocation().directionTo(objective).ordinal();
            for (int o : DIRECTION_OFFSETS) {
                Direction d = DIRECTIONS[(i + o) % 8];
                if (rc.canBuildRobot(type, d, influence)) {
                    // if robot can be spawned, add it to units set
                    rc.buildRobot(type, d, influence);
                    scanner.registerUnit(rc.senseRobotAtLocation(rc.adjacentLocation(d)), politicianType);

                    return politicianType == null ? 0 : (F_V_EC_SPAWN_POLITICIAN | politicianType.spawnInstruction);
                }
            }

            // return false if no valid build locations
            return -1;
        }
    }

    private void computeTargets() {
        // make defensive politicians protect the EC and otherwise protect slanderers that are being attacked

        int defensive = Politician.Type.DEFENSIVE.ordinal();
        targets[defensive] = rc.getLocation();
        if (scanner.panicSlanderer != null)
            targets[defensive] = scanner.panicSlanderer;
        if (scanner.nearestEnemyLocation != null)
            targets[defensive] = scanner.nearestEnemyLocation;

        if (DEBUG && !targets[defensive].equals(rc.getLocation()))
            rc.setIndicatorLine(rc.getLocation(), targets[defensive], 0, 255, 0);

        // make offensive politicians attack the nearest unowned EC

        MapLocation nearestUnownedEC = rc.getLocation();
        int minD2 = Integer.MAX_VALUE;
        for (Map.Entry<MapLocation, Integer> otherEc : scanner.ecs.entrySet()) {
            MapLocation location = otherEc.getKey();
            if (location.equals(rc.getLocation()) || otherEc.getValue() != null)
                continue;
            int d2 = rc.getLocation().distanceSquaredTo(location);
            if (d2 < minD2) {
                minD2 = d2;
                nearestUnownedEC = location;
            }
        }

        targets[Politician.Type.OFFENSIVE.ordinal()] = nearestUnownedEC;
        if (DEBUG && !nearestUnownedEC.equals(rc.getLocation()))
            rc.setIndicatorLine(rc.getLocation(), nearestUnownedEC, 255, 0, 0);
    }

}

package mars.robot;

import battlecode.common.*;

import static mars.Constants.F_V_SLANDERER_PANIC;

public strictfp class Slanderer extends Unit {

    private MapLocation closestBorder;
    private boolean panic;

    public Slanderer(RobotController rc) throws GameActionException {
        super(rc);
    }

    @Override
    public void step() throws GameActionException {
        panic = false;

        // Moves to the closest know edge
        MapLocation ownLocation = rc.getLocation();

        if (!fleeMuckrakers(ownLocation))
            moveToSide();
    }

    @Override
    protected int getAlternativeFlagMessage() {
        return panic ? F_V_SLANDERER_PANIC | encodeLocation(rc.getLocation()) : 0;
    }

    private boolean fleeMuckrakers(MapLocation ownLocation) throws GameActionException {
        RobotInfo[] enemies = this.scanEnemy();
        if (enemies.length > 0) {
            int dangerX = 0;
            int dangerY = 0;
            for (RobotInfo enemy : enemies) {
                if (enemy.getType() == RobotType.MUCKRAKER) {
                    panic = true;
                    MapLocation enemyLoc = enemy.getLocation();
                    if (enemyLoc.x > ownLocation.x) {
                        dangerX--;
                    } else {
                        dangerX++;
                    }

                    if (enemyLoc.y > ownLocation.y) {
                        dangerY--;
                    } else {
                        dangerY++;
                    }
                }
            }

            MapLocation safeDir = ownLocation.translate(Integer.signum(dangerX), Integer.signum(dangerY));
            this.tryMove(safeDir);
            return true;
        }
        return false;
    }

    private boolean moveToSide() throws GameActionException {
        rc.setIndicatorDot(rc.getLocation(), 0, 0, 0);
        if (this.closestBorder == null) {
            this.closestBorder = this.senseBorder();
        }

        RobotInfo botOnBorder = senseLoc(this.closestBorder);
        if (botOnBorder != null) {
            // Place on border is already taken.
            // Set closestBorder to null so we take a random move and recalculate the border again.
            // Not the best solution but robust.
            this.closestBorder = null;
        }


        if (this.closestBorder != null) {
            rc.setIndicatorDot(this.closestBorder, 255, 0, 0);
            rc.setIndicatorLine(rc.getLocation(), this.closestBorder, 128, 128, 128);
            if (this.closestBorder.equals(rc.getLocation())) {
                Direction borderDir = borderDirection();
                if (borderDir != null) {
                    return tryMove(borderDir.rotateLeft().rotateLeft()) ||
                            tryMove(borderDir.rotateRight().rotateRight());
                }
            }
            return this.tryMove(this.closestBorder);
        } else {
            return this.randomMove();
        }
    }

    private Direction borderDirection() throws GameActionException {
        MapLocation ownLocation = rc.getLocation();
        if (!rc.onTheMap(ownLocation.add(Direction.EAST))) {
            return Direction.EAST;
        } else if (!rc.onTheMap(ownLocation.add(Direction.WEST))) {
            return Direction.WEST;
        } else if (!rc.onTheMap(ownLocation.add(Direction.NORTH))) {
            return Direction.NORTH;
        } else if (!rc.onTheMap(ownLocation.add(Direction.SOUTH))) {
            return Direction.SOUTH;
        }
        return null;
    }

}

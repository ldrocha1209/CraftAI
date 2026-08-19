package com.lucasrocha.craftai.client.service;

import com.lucasrocha.craftai.client.data.WorldQueryResult;

public final class NavigationService {

    private static final double OCTANT_RADIANS = Math.PI / 4.0;
    private static final double HALF_OCTANT_RADIANS = OCTANT_RADIANS / 2.0;
    private static final WorldQueryResult.Direction[] DIRECTIONS_CLOCKWISE_FROM_EAST = {
            WorldQueryResult.Direction.EAST,
            WorldQueryResult.Direction.SOUTHEAST,
            WorldQueryResult.Direction.SOUTH,
            WorldQueryResult.Direction.SOUTHWEST,
            WorldQueryResult.Direction.WEST,
            WorldQueryResult.Direction.NORTHWEST,
            WorldQueryResult.Direction.NORTH,
            WorldQueryResult.Direction.NORTHEAST
    };

    private NavigationService() {}

    public static WorldQueryResult.Navigation calculate(
            long originX,
            long originZ,
            long destinationX,
            long destinationZ
    ) {
        long deltaX = destinationX - originX;
        long deltaZ = destinationZ - originZ;
        int distanceBlocks = (int) Math.round(Math.hypot(deltaX, deltaZ));

        return new WorldQueryResult.Navigation(
                distanceBlocks,
                Math.toIntExact(deltaX),
                Math.toIntExact(deltaZ),
                direction(deltaX, deltaZ)
        );
    }

    private static WorldQueryResult.Direction direction(long deltaX, long deltaZ) {
        if (deltaX == 0 && deltaZ == 0) {
            return WorldQueryResult.Direction.HERE;
        }

        double angle = Math.atan2(deltaZ, deltaX);
        double clockwiseAngle = (angle + Math.PI * 2.0) % (Math.PI * 2.0);
        int octant = (int) Math.floor(
                (clockwiseAngle + HALF_OCTANT_RADIANS) / OCTANT_RADIANS
        ) % DIRECTIONS_CLOCKWISE_FROM_EAST.length;
        return DIRECTIONS_CLOCKWISE_FROM_EAST[octant];
    }
}

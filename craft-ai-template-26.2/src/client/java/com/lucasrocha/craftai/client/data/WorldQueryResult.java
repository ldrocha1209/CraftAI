package com.lucasrocha.craftai.client.data;

public class WorldQueryResult {

    public enum Kind {
        STRUCTURE,
        BIOME
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        UNSUPPORTED
    }

    public record Position(
            int x,
            int z
    ) {}

    private final Kind kind;
    private final String target;
    private final Status status;
    private final String dimension;
    private final Position position;
    private final Integer distanceBlocks;
    private final String reason;

    private WorldQueryResult(
            Kind kind,
            String target,
            Status status,
            String dimension,
            Position position,
            Integer distanceBlocks,
            String reason
    ) {
        this.kind = kind;
        this.target = target;
        this.status = status;
        this.dimension = dimension;
        this.position = position;
        this.distanceBlocks = distanceBlocks;
        this.reason = reason;
    }

    public static WorldQueryResult found(
            Kind kind,
            WorldQueryTarget target,
            String dimension,
            int x,
            int z,
            int distanceBlocks
    ) {
        return new WorldQueryResult(
                kind,
                target.identifier(),
                Status.FOUND,
                dimension,
                new Position(x, z),
                distanceBlocks,
                null
        );
    }

    public static WorldQueryResult notFound(
            Kind kind,
            WorldQueryTarget target,
            String dimension
    ) {
        return new WorldQueryResult(
                kind,
                target.identifier(),
                Status.NOT_FOUND,
                dimension,
                null,
                null,
                null
        );
    }

    public static WorldQueryResult unsupported(
            Kind kind,
            WorldQueryTarget target,
            String dimension,
            String reason
    ) {
        return new WorldQueryResult(
                kind,
                target.identifier(),
                Status.UNSUPPORTED,
                dimension,
                null,
                null,
                reason
        );
    }

    public boolean isFound() {
        return status == Status.FOUND && position != null;
    }

    public boolean isReusable() {
        return status == Status.FOUND || status == Status.NOT_FOUND;
    }

    public String dimension() {
        return dimension;
    }

    public WorldQueryResult withDistanceFrom(long x, long z) {
        if (!isFound()) {
            return this;
        }

        long deltaX = position.x() - x;
        long deltaZ = position.z() - z;
        int updatedDistance = (int) Math.round(Math.sqrt(deltaX * deltaX + deltaZ * deltaZ));

        return new WorldQueryResult(
                kind,
                target,
                status,
                dimension,
                position,
                updatedDistance,
                reason
        );
    }
}

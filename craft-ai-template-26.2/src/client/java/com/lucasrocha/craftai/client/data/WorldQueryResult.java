package com.lucasrocha.craftai.client.data;

public class WorldQueryResult {

    public enum Kind {
        STRUCTURE,
        BIOME
    }

    public enum Target {
        VILLAGE,
        DESERT
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
    private final Target target;
    private final Status status;
    private final String dimension;
    private final Position position;
    private final Integer distanceBlocks;
    private final String reason;

    private WorldQueryResult(
            Kind kind,
            Target target,
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
            Target target,
            String dimension,
            int x,
            int z,
            int distanceBlocks
    ) {
        return new WorldQueryResult(
                kind,
                target,
                Status.FOUND,
                dimension,
                new Position(x, z),
                distanceBlocks,
                null
        );
    }

    public static WorldQueryResult notFound(
            Kind kind,
            Target target,
            String dimension
    ) {
        return new WorldQueryResult(
                kind,
                target,
                Status.NOT_FOUND,
                dimension,
                null,
                null,
                null
        );
    }

    public static WorldQueryResult unsupported(
            Kind kind,
            Target target,
            String dimension,
            String reason
    ) {
        return new WorldQueryResult(
                kind,
                target,
                Status.UNSUPPORTED,
                dimension,
                null,
                null,
                reason
        );
    }
}

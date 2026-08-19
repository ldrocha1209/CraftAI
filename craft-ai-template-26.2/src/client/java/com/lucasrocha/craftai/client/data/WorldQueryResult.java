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

    public enum Direction {
        NORTH,
        NORTHEAST,
        EAST,
        SOUTHEAST,
        SOUTH,
        SOUTHWEST,
        WEST,
        NORTHWEST,
        HERE
    }

    public record Position(
            int x,
            int z
    ) {}

    public record Navigation(
            int distanceBlocks,
            int deltaXBlocks,
            int deltaZBlocks,
            Direction direction
    ) {
        public Navigation {
            if (distanceBlocks < 0 || direction == null) {
                throw new IllegalArgumentException(
                        "Navigation requires a non-negative distance and direction."
                );
            }
        }
    }

    private final Kind kind;
    private final String target;
    private final Status status;
    private final String dimension;
    private final Position position;
    private final Navigation navigation;
    private final String reason;

    private WorldQueryResult(
            Kind kind,
            String target,
            Status status,
            String dimension,
            Position position,
            Navigation navigation,
            String reason
    ) {
        if (status == Status.FOUND && (position == null || navigation == null)) {
            throw new IllegalArgumentException(
                    "A found world-query result requires position and navigation data."
            );
        }
        if (status != Status.FOUND && (position != null || navigation != null)) {
            throw new IllegalArgumentException(
                    "Only a found world-query result may contain position or navigation data."
            );
        }
        this.kind = kind;
        this.target = target;
        this.status = status;
        this.dimension = dimension;
        this.position = position;
        this.navigation = navigation;
        this.reason = reason;
    }

    public static WorldQueryResult found(
            Kind kind,
            WorldQueryTarget target,
            String dimension,
            int x,
            int z,
            Navigation navigation
    ) {
        return new WorldQueryResult(
                kind,
                target.identifier(),
                Status.FOUND,
                dimension,
                new Position(x, z),
                navigation,
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

    public Kind kind() {
        return kind;
    }

    public String target() {
        return target;
    }

    public boolean isReusable() {
        return status == Status.FOUND || status == Status.NOT_FOUND;
    }

    public String dimension() {
        return dimension;
    }

    public WorldQueryResult withNavigation(Navigation updatedNavigation) {
        if (!isFound()) {
            return this;
        }

        return new WorldQueryResult(
                kind,
                target,
                status,
                dimension,
                position,
                updatedNavigation,
                reason
        );
    }

    public Position position() {
        return position;
    }
}

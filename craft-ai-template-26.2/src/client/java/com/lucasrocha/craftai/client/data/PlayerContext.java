package com.lucasrocha.craftai.client.data;

import java.util.Map;

public class PlayerContext {

    public record Position(
            long x,
            long y,
            long z
    ) {}

    public record Equipment(
            String mainHand,
            String offHand,
            String helmet,
            String chestplate,
            String leggings,
            String boots
    ) {}

    private final String gameMode;
    private final String biome;
    private final String timeOfDay;
    private final String dimension;
    private final Position position;
    private final Map<String, Integer> inventory;
    private final Equipment equipment;

    public PlayerContext(
            String gameMode,
            String biome,
            String timeOfDay,
            String dimension,
            Position position,
            Map<String, Integer> inventory,
            Equipment equipment
    ) {
        this.gameMode = gameMode;
        this.biome = biome;
        this.timeOfDay = timeOfDay;
        this.dimension = dimension;
        this.position = position;
        this.inventory = Map.copyOf(inventory);
        this.equipment = equipment;
    }

    public String getDimension() {
        return dimension;
    }
}

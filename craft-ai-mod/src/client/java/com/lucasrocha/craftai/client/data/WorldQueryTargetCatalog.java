package com.lucasrocha.craftai.client.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class WorldQueryTargetCatalog {

    private static final int DEFAULT_STRUCTURE_SEARCH_RADIUS = 2;
    private static final int STRONGHOLD_SEARCH_RADIUS = 100;

    public static final WorldQueryTarget VILLAGE = structure(
            "village",
            DEFAULT_STRUCTURE_SEARCH_RADIUS,
            List.of(
                    "village_desert", "village_plains", "village_savanna",
                    "village_snowy", "village_taiga"
            ),
            "villager", "villagers"
    );
    public static final WorldQueryTarget STRONGHOLD = structure(
            "stronghold", STRONGHOLD_SEARCH_RADIUS, List.of("stronghold")
    );
    public static final WorldQueryTarget DESERT = biome("desert");

    private static final List<WorldQueryTarget> BIOMES = List.of(
            biome("badlands", "mesa"),
            biome("bamboo_jungle"),
            biome("basalt_deltas"),
            biome("beach"),
            biome("birch_forest"),
            biome("cherry_grove"),
            biome("cold_ocean"),
            biome("crimson_forest"),
            biome("dark_forest"),
            biome("deep_cold_ocean"),
            biome("deep_dark"),
            biome("deep_frozen_ocean"),
            biome("deep_lukewarm_ocean"),
            biome("deep_ocean"),
            DESERT,
            biome("dripstone_caves"),
            biome("end_barrens"),
            biome("end_highlands"),
            biome("end_midlands"),
            biome("eroded_badlands"),
            biome("flower_forest"),
            biome("forest"),
            biome("frozen_ocean"),
            biome("frozen_peaks"),
            biome("frozen_river"),
            biome("grove"),
            biome("ice_spikes"),
            biome("jagged_peaks"),
            biome("jungle"),
            biome("lukewarm_ocean"),
            biome("lush_caves"),
            biome("mangrove_swamp"),
            biome("meadow"),
            biome("mushroom_fields", "mushroom island", "mooshroom island"),
            biome("nether_wastes"),
            biome("ocean"),
            biome("old_growth_birch_forest"),
            biome("old_growth_pine_taiga"),
            biome("old_growth_spruce_taiga"),
            biome("pale_garden"),
            biome("plains"),
            biome("river"),
            biome("savanna"),
            biome("savanna_plateau"),
            biome("small_end_islands"),
            biome("snowy_beach"),
            biome("snowy_plains", "snowy tundra"),
            biome("snowy_slopes"),
            biome("snowy_taiga"),
            biome("soul_sand_valley"),
            biome("sparse_jungle"),
            biome("stony_peaks"),
            biome("stony_shore"),
            biome("sulfur_caves"),
            biome("sunflower_plains"),
            biome("swamp"),
            biome("taiga"),
            biome("the_end", "end biome"),
            biome("the_void", "void biome"),
            biome("warm_ocean"),
            biome("warped_forest"),
            biome("windswept_forest"),
            biome("windswept_gravelly_hills"),
            biome("windswept_hills", "extreme hills"),
            biome("windswept_savanna"),
            biome("wooded_badlands")
    );

    private static final List<WorldQueryTarget> STRUCTURES = List.of(
            structure("ancient_city", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("ancient_city")),
            structure("bastion_remnant", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("bastion_remnant"), "bastion"),
            structure("buried_treasure", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("buried_treasure"), "treasure"),
            structure("desert_pyramid", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("desert_pyramid"), "desert temple"),
            structure("end_city", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("end_city")),
            structure("nether_fortress", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("fortress"), "fortress"),
            structure("igloo", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("igloo")),
            structure("jungle_pyramid", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("jungle_pyramid"), "jungle temple"),
            structure("woodland_mansion", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("mansion"), "mansion"),
            structure("mineshaft", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("mineshaft", "mineshaft_mesa"), "mine shaft"),
            structure("ocean_monument", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("monument"), "monument"),
            structure("nether_fossil", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("nether_fossil")),
            structure("ocean_ruin", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("ocean_ruin_cold", "ocean_ruin_warm")),
            structure("pillager_outpost", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("pillager_outpost"), "outpost"),
            structure(
                    "ruined_portal",
                    DEFAULT_STRUCTURE_SEARCH_RADIUS,
                    List.of(
                            "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle",
                            "ruined_portal_mountain", "ruined_portal_nether",
                            "ruined_portal_ocean", "ruined_portal_swamp"
                    )
            ),
            structure("shipwreck", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("shipwreck", "shipwreck_beached")),
            STRONGHOLD,
            structure("swamp_hut", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("swamp_hut"), "witch hut"),
            structure("trail_ruins", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("trail_ruins")),
            structure("trial_chambers", DEFAULT_STRUCTURE_SEARCH_RADIUS, List.of("trial_chambers"), "trial chamber"),
            VILLAGE
    );

    private static final List<WorldQueryTarget> ALL_TARGETS = combineTargets();
    private static final Map<String, WorldQueryTarget> TARGETS_BY_ID = ALL_TARGETS.stream()
            .collect(Collectors.toUnmodifiableMap(WorldQueryTarget::identifier, Function.identity()));

    private WorldQueryTargetCatalog() {}

    public static List<WorldQueryTarget> all() {
        return ALL_TARGETS;
    }

    public static List<WorldQueryTarget> biomes() {
        return BIOMES;
    }

    public static List<WorldQueryTarget> structures() {
        return STRUCTURES;
    }

    public static WorldQueryTarget require(String identifier) {
        WorldQueryTarget target = TARGETS_BY_ID.get(identifier);
        if (target == null) {
            throw new IllegalArgumentException("Unknown world-query target: " + identifier);
        }
        return target;
    }

    private static List<WorldQueryTarget> combineTargets() {
        List<WorldQueryTarget> targets = new ArrayList<>(BIOMES.size() + STRUCTURES.size());
        targets.addAll(BIOMES);
        targets.addAll(STRUCTURES);
        return List.copyOf(targets);
    }

    private static WorldQueryTarget biome(String path, String... additionalAliases) {
        return target(
                WorldQueryResult.Kind.BIOME,
                path,
                List.of(path),
                0,
                additionalAliases
        );
    }

    private static WorldQueryTarget structure(
            String path,
            int structureSearchRadius,
            List<String> registryPaths,
            String... additionalAliases
    ) {
        return target(
                WorldQueryResult.Kind.STRUCTURE,
                path,
                registryPaths,
                structureSearchRadius,
                additionalAliases
        );
    }

    private static WorldQueryTarget target(
            WorldQueryResult.Kind kind,
            String path,
            List<String> registryPaths,
            int structureSearchRadius,
            String... additionalAliases
    ) {
        String displayName = path.replace('_', ' ');
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add(displayName);
        aliases.add(pluralize(displayName));
        aliases.add(singularize(displayName));
        aliases.addAll(Arrays.asList(additionalAliases));

        return new WorldQueryTarget(
                kind,
                minecraftId(path),
                displayName,
                aliases.stream()
                        .map(alias -> alias.toLowerCase(Locale.ROOT))
                        .toList(),
                registryPaths.stream().map(WorldQueryTargetCatalog::minecraftId).toList(),
                structureSearchRadius
        );
    }

    private static String minecraftId(String path) {
        return "minecraft:" + path;
    }

    private static String pluralize(String phrase) {
        int finalSpace = phrase.lastIndexOf(' ');
        String prefix = finalSpace < 0 ? "" : phrase.substring(0, finalSpace + 1);
        String word = finalSpace < 0 ? phrase : phrase.substring(finalSpace + 1);

        if (word.endsWith("s")) {
            return phrase;
        }
        if (word.endsWith("y") && word.length() > 1) {
            return prefix + word.substring(0, word.length() - 1) + "ies";
        }
        if (word.endsWith("ch") || word.endsWith("sh") || word.endsWith("x")) {
            return prefix + word + "es";
        }
        return prefix + word + "s";
    }

    private static String singularize(String phrase) {
        int finalSpace = phrase.lastIndexOf(' ');
        String prefix = finalSpace < 0 ? "" : phrase.substring(0, finalSpace + 1);
        String word = finalSpace < 0 ? phrase : phrase.substring(finalSpace + 1);

        if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 1) {
            return prefix + word.substring(0, word.length() - 1);
        }
        return phrase;
    }
}

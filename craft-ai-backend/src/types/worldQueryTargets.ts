export const BIOME_WORLD_QUERY_TARGETS = [
    "minecraft:badlands",
    "minecraft:bamboo_jungle",
    "minecraft:basalt_deltas",
    "minecraft:beach",
    "minecraft:birch_forest",
    "minecraft:cherry_grove",
    "minecraft:cold_ocean",
    "minecraft:crimson_forest",
    "minecraft:dark_forest",
    "minecraft:deep_cold_ocean",
    "minecraft:deep_dark",
    "minecraft:deep_frozen_ocean",
    "minecraft:deep_lukewarm_ocean",
    "minecraft:deep_ocean",
    "minecraft:desert",
    "minecraft:dripstone_caves",
    "minecraft:end_barrens",
    "minecraft:end_highlands",
    "minecraft:end_midlands",
    "minecraft:eroded_badlands",
    "minecraft:flower_forest",
    "minecraft:forest",
    "minecraft:frozen_ocean",
    "minecraft:frozen_peaks",
    "minecraft:frozen_river",
    "minecraft:grove",
    "minecraft:ice_spikes",
    "minecraft:jagged_peaks",
    "minecraft:jungle",
    "minecraft:lukewarm_ocean",
    "minecraft:lush_caves",
    "minecraft:mangrove_swamp",
    "minecraft:meadow",
    "minecraft:mushroom_fields",
    "minecraft:nether_wastes",
    "minecraft:ocean",
    "minecraft:old_growth_birch_forest",
    "minecraft:old_growth_pine_taiga",
    "minecraft:old_growth_spruce_taiga",
    "minecraft:pale_garden",
    "minecraft:plains",
    "minecraft:river",
    "minecraft:savanna",
    "minecraft:savanna_plateau",
    "minecraft:small_end_islands",
    "minecraft:snowy_beach",
    "minecraft:snowy_plains",
    "minecraft:snowy_slopes",
    "minecraft:snowy_taiga",
    "minecraft:soul_sand_valley",
    "minecraft:sparse_jungle",
    "minecraft:stony_peaks",
    "minecraft:stony_shore",
    "minecraft:sulfur_caves",
    "minecraft:sunflower_plains",
    "minecraft:swamp",
    "minecraft:taiga",
    "minecraft:the_end",
    "minecraft:the_void",
    "minecraft:warm_ocean",
    "minecraft:warped_forest",
    "minecraft:windswept_forest",
    "minecraft:windswept_gravelly_hills",
    "minecraft:windswept_hills",
    "minecraft:windswept_savanna",
    "minecraft:wooded_badlands"
] as const;

export const STRUCTURE_WORLD_QUERY_TARGETS = [
    "minecraft:ancient_city",
    "minecraft:bastion_remnant",
    "minecraft:buried_treasure",
    "minecraft:desert_pyramid",
    "minecraft:end_city",
    "minecraft:nether_fortress",
    "minecraft:igloo",
    "minecraft:jungle_pyramid",
    "minecraft:woodland_mansion",
    "minecraft:mineshaft",
    "minecraft:ocean_monument",
    "minecraft:nether_fossil",
    "minecraft:ocean_ruin",
    "minecraft:pillager_outpost",
    "minecraft:ruined_portal",
    "minecraft:shipwreck",
    "minecraft:stronghold",
    "minecraft:swamp_hut",
    "minecraft:trail_ruins",
    "minecraft:trial_chambers",
    "minecraft:village"
] as const;

export const WORLD_QUERY_TARGETS = [
    ...BIOME_WORLD_QUERY_TARGETS,
    ...STRUCTURE_WORLD_QUERY_TARGETS
] as const;

export type BiomeWorldQueryTarget = typeof BIOME_WORLD_QUERY_TARGETS[number];
export type StructureWorldQueryTarget = typeof STRUCTURE_WORLD_QUERY_TARGETS[number];
export type WorldQueryTarget = typeof WORLD_QUERY_TARGETS[number];

const BIOME_TARGET_SET = new Set<string>(BIOME_WORLD_QUERY_TARGETS);

export function worldQueryKindForTarget(
    target: WorldQueryTarget
): "STRUCTURE" | "BIOME" {
    return BIOME_TARGET_SET.has(target) ? "BIOME" : "STRUCTURE";
}

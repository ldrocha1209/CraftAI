import type { WorldQueryTarget } from "./worldQueryTargets.js";

export type { WorldQueryTarget } from "./worldQueryTargets.js";

export interface PlayerPosition {
    x: number;
    y: number;
    z: number;
}

export interface PlayerEquipment {
    mainHand: string;
    offHand: string;
    helmet: string;
    chestplate: string;
    leggings: string;
    boots: string;
}

export interface PlayerContext {
    gameMode: string;
    biome: string;
    timeOfDay: string;
    dimension: string;
    position?: PlayerPosition;
    inventory: Record<string, number>;
    equipment: PlayerEquipment;
}

export interface MatchedItem {
    id: string;
    name: string;
    maxStackSize: number;
}

export interface MinecraftRecipe {
    recipeId: string;
    ingredients: Record<string, number>;
}

export type WorldQueryKind = "STRUCTURE" | "BIOME";
export type WorldQueryStatus = "FOUND" | "NOT_FOUND" | "UNSUPPORTED";
export type NavigationDirection =
    | "NORTH"
    | "NORTHEAST"
    | "EAST"
    | "SOUTHEAST"
    | "SOUTH"
    | "SOUTHWEST"
    | "WEST"
    | "NORTHWEST"
    | "HERE";

export interface WorldQueryPosition {
    x: number;
    z: number;
}

export interface WorldQueryNavigation {
    distanceBlocks: number;
    deltaXBlocks: number;
    deltaZBlocks: number;
    direction: NavigationDirection;
}

export interface WorldQueryResult {
    kind: WorldQueryKind;
    target: WorldQueryTarget;
    status: WorldQueryStatus;
    dimension: string;
    position?: WorldQueryPosition;
    navigation?: WorldQueryNavigation;
    reason?: string;
}

export interface AskRequest {
    question: string;
    player: PlayerContext;
    matchedItem?: MatchedItem;
    recipe?: MinecraftRecipe;
    worldQuery?: WorldQueryResult;
}

export interface AskResponse {
    answer: string;
}

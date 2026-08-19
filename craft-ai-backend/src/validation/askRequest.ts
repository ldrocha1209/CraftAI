import type {
    AskRequest,
    MatchedItem,
    MinecraftRecipe,
    NavigationDirection,
    PlayerContext,
    PlayerEquipment,
    PlayerPosition,
    WorldQueryKind,
    WorldQueryNavigation,
    WorldQueryPosition,
    WorldQueryResult,
    WorldQueryStatus,
    WorldQueryTarget
} from "../types/ask.js";
import {
    WORLD_QUERY_TARGETS,
    worldQueryKindForTarget
} from "../types/worldQueryTargets.js";

export class RequestValidationError extends Error {
    constructor(public readonly issues: string[]) {
        super("The request body is invalid.");
        this.name = "RequestValidationError";
    }
}

export function parseAskRequest(value: unknown): AskRequest {
    const issues: string[] = [];
    const body = readRecord(value, "body", issues);

    const question = readString(body.question, "question", issues, true);
    const player = parsePlayerContext(body.player, issues);
    const matchedItem = parseOptionalMatchedItem(body.matchedItem, issues);
    const recipe = parseOptionalRecipe(body.recipe, issues);
    const worldQuery = parseOptionalWorldQuery(body.worldQuery, player.position, issues);

    if (issues.length > 0) {
        throw new RequestValidationError(issues);
    }

    return {
        question,
        player,
        ...(matchedItem ? { matchedItem } : {}),
        ...(recipe ? { recipe } : {}),
        ...(worldQuery ? { worldQuery } : {})
    };
}

function parsePlayerContext(value: unknown, issues: string[]): PlayerContext {
    const player = readRecord(value, "player", issues);

    return {
        gameMode: readString(player.gameMode, "player.gameMode", issues),
        biome: readString(player.biome, "player.biome", issues),
        timeOfDay: readString(player.timeOfDay, "player.timeOfDay", issues),
        dimension: readString(player.dimension, "player.dimension", issues),
        ...parseOptionalPosition(player.position, issues),
        inventory: readCountMap(player.inventory, "player.inventory", issues),
        equipment: parseEquipment(player.equipment, issues)
    };
}

function parseOptionalPosition(
    value: unknown,
    issues: string[]
): { position?: PlayerPosition } {
    if (value === undefined || value === null) {
        return {};
    }

    const position = readRecord(value, "player.position", issues);

    return {
        position: {
            x: readInteger(position.x, "player.position.x", issues),
            y: readInteger(position.y, "player.position.y", issues),
            z: readInteger(position.z, "player.position.z", issues)
        }
    };
}

function parseEquipment(value: unknown, issues: string[]): PlayerEquipment {
    const equipment = readRecord(value, "player.equipment", issues);

    return {
        mainHand: readString(equipment.mainHand, "player.equipment.mainHand", issues),
        offHand: readString(equipment.offHand, "player.equipment.offHand", issues),
        helmet: readString(equipment.helmet, "player.equipment.helmet", issues),
        chestplate: readString(equipment.chestplate, "player.equipment.chestplate", issues),
        leggings: readString(equipment.leggings, "player.equipment.leggings", issues),
        boots: readString(equipment.boots, "player.equipment.boots", issues)
    };
}

function parseOptionalMatchedItem(
    value: unknown,
    issues: string[]
): MatchedItem | undefined {
    if (value === undefined || value === null) {
        return undefined;
    }

    const item = readRecord(value, "matchedItem", issues);

    return {
        id: readString(item.id, "matchedItem.id", issues),
        name: readString(item.name, "matchedItem.name", issues),
        maxStackSize: readNonNegativeInteger(
            item.maxStackSize,
            "matchedItem.maxStackSize",
            issues
        )
    };
}

function parseOptionalRecipe(
    value: unknown,
    issues: string[]
): MinecraftRecipe | undefined {
    if (value === undefined || value === null) {
        return undefined;
    }

    const recipe = readRecord(value, "recipe", issues);

    return {
        recipeId: readString(recipe.recipeId, "recipe.recipeId", issues),
        ingredients: readCountMap(recipe.ingredients, "recipe.ingredients", issues)
    };
}

function parseOptionalWorldQuery(
    value: unknown,
    playerPosition: PlayerPosition | undefined,
    issues: string[]
): WorldQueryResult | undefined {
    if (value === undefined || value === null) {
        return undefined;
    }

    const query = readRecord(value, "worldQuery", issues);
    const kind = readEnum(
        query.kind,
        "worldQuery.kind",
        ["STRUCTURE", "BIOME"] as const,
        issues
    ) as WorldQueryKind;
    const target = readEnum(
        query.target,
        "worldQuery.target",
        WORLD_QUERY_TARGETS,
        issues
    ) as WorldQueryTarget;
    const status = readEnum(
        query.status,
        "worldQuery.status",
        ["FOUND", "NOT_FOUND", "UNSUPPORTED"] as const,
        issues
    ) as WorldQueryStatus;
    const dimension = readString(query.dimension, "worldQuery.dimension", issues);

    const expectedKind = worldQueryKindForTarget(target);
    if (kind !== expectedKind) {
        issues.push(`worldQuery.kind must be ${expectedKind} for target ${target}.`);
    }

    let position: WorldQueryPosition | undefined;
    let navigation: WorldQueryNavigation | undefined;

    if (status === "FOUND") {
        const rawPosition = readRecord(query.position, "worldQuery.position", issues);
        position = {
            x: readInteger(rawPosition.x, "worldQuery.position.x", issues),
            z: readInteger(rawPosition.z, "worldQuery.position.z", issues)
        };
        navigation = parseWorldQueryNavigation(query.navigation, issues);
        validateNavigationConsistency(position, navigation, playerPosition, issues);
    } else {
        if (query.position !== undefined && query.position !== null) {
            issues.push("worldQuery.position is only allowed for a FOUND result.");
        }
        if (query.navigation !== undefined && query.navigation !== null) {
            issues.push("worldQuery.navigation is only allowed for a FOUND result.");
        }
    }

    if (query.distanceBlocks !== undefined) {
        issues.push(
            "worldQuery.distanceBlocks was replaced by worldQuery.navigation.distanceBlocks."
        );
    }

    const reason = readOptionalString(query.reason, "worldQuery.reason", issues);

    return {
        kind,
        target,
        status,
        dimension,
        ...(position ? { position } : {}),
        ...(navigation ? { navigation } : {}),
        ...(reason ? { reason } : {})
    };
}

function parseWorldQueryNavigation(
    value: unknown,
    issues: string[]
): WorldQueryNavigation {
    const navigation = readRecord(value, "worldQuery.navigation", issues);
    return {
        distanceBlocks: readNonNegativeInteger(
            navigation.distanceBlocks,
            "worldQuery.navigation.distanceBlocks",
            issues
        ),
        deltaXBlocks: readInteger(
            navigation.deltaXBlocks,
            "worldQuery.navigation.deltaXBlocks",
            issues
        ),
        deltaZBlocks: readInteger(
            navigation.deltaZBlocks,
            "worldQuery.navigation.deltaZBlocks",
            issues
        ),
        direction: readEnum(
            navigation.direction,
            "worldQuery.navigation.direction",
            [
                "NORTH", "NORTHEAST", "EAST", "SOUTHEAST", "SOUTH",
                "SOUTHWEST", "WEST", "NORTHWEST", "HERE"
            ] as const,
            issues
        ) as NavigationDirection
    };
}

function validateNavigationConsistency(
    destination: WorldQueryPosition,
    navigation: WorldQueryNavigation,
    playerPosition: PlayerPosition | undefined,
    issues: string[]
): void {
    if (!playerPosition) {
        return;
    }

    const expectedDeltaX = destination.x - playerPosition.x;
    const expectedDeltaZ = destination.z - playerPosition.z;
    const expectedDistance = Math.round(Math.hypot(expectedDeltaX, expectedDeltaZ));
    const expectedDirection = navigationDirection(expectedDeltaX, expectedDeltaZ);

    if (navigation.deltaXBlocks !== expectedDeltaX) {
        issues.push("worldQuery.navigation.deltaXBlocks does not match the supplied positions.");
    }
    if (navigation.deltaZBlocks !== expectedDeltaZ) {
        issues.push("worldQuery.navigation.deltaZBlocks does not match the supplied positions.");
    }
    if (navigation.distanceBlocks !== expectedDistance) {
        issues.push("worldQuery.navigation.distanceBlocks does not match the supplied positions.");
    }
    if (navigation.direction !== expectedDirection) {
        issues.push("worldQuery.navigation.direction does not match the supplied positions.");
    }
}

function navigationDirection(deltaX: number, deltaZ: number): NavigationDirection {
    if (deltaX === 0 && deltaZ === 0) {
        return "HERE";
    }

    const directions: readonly NavigationDirection[] = [
        "EAST", "SOUTHEAST", "SOUTH", "SOUTHWEST",
        "WEST", "NORTHWEST", "NORTH", "NORTHEAST"
    ];
    const octantRadians = Math.PI / 4;
    const angle = Math.atan2(deltaZ, deltaX);
    const clockwiseAngle = (angle + Math.PI * 2) % (Math.PI * 2);
    const octant = Math.floor(
        (clockwiseAngle + octantRadians / 2) / octantRadians
    ) % directions.length;
    return directions[octant];
}

function readRecord(
    value: unknown,
    path: string,
    issues: string[]
): Record<string, unknown> {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
        issues.push(`${path} must be an object.`);
        return {};
    }

    return value as Record<string, unknown>;
}

function readString(
    value: unknown,
    path: string,
    issues: string[],
    requireNonBlank = false
): string {
    if (typeof value !== "string") {
        issues.push(`${path} must be a string.`);
        return "";
    }

    if (requireNonBlank && value.trim().length === 0) {
        issues.push(`${path} must not be blank.`);
    }

    return value;
}

function readOptionalString(
    value: unknown,
    path: string,
    issues: string[]
): string | undefined {
    if (value === undefined || value === null) {
        return undefined;
    }

    return readString(value, path, issues);
}

function readInteger(value: unknown, path: string, issues: string[]): number {
    if (typeof value !== "number" || !Number.isSafeInteger(value)) {
        issues.push(`${path} must be a safe integer.`);
        return 0;
    }

    return value;
}

function readNonNegativeInteger(
    value: unknown,
    path: string,
    issues: string[]
): number {
    const number = readInteger(value, path, issues);

    if (number < 0) {
        issues.push(`${path} must not be negative.`);
    }

    return number;
}

function readCountMap(
    value: unknown,
    path: string,
    issues: string[]
): Record<string, number> {
    const record = readRecord(value, path, issues);
    const counts: Record<string, number> = {};

    for (const [key, count] of Object.entries(record)) {
        counts[key] = readNonNegativeInteger(count, `${path}.${key}`, issues);
    }

    return counts;
}

function readEnum<const T extends readonly string[]>(
    value: unknown,
    path: string,
    allowed: T,
    issues: string[]
): T[number] {
    if (typeof value === "string" && (allowed as readonly string[]).includes(value)) {
        return value as T[number];
    }

    issues.push(`${path} must be one of: ${allowed.join(", ")}.`);
    return allowed[0];
}

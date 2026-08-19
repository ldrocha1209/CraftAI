import type {
    AskRequest,
    MatchedItem,
    MinecraftRecipe,
    PlayerContext,
    PlayerEquipment,
    PlayerPosition,
    WorldQueryKind,
    WorldQueryPosition,
    WorldQueryResult,
    WorldQueryStatus,
    WorldQueryTarget
} from "../types/ask.js";

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
    const worldQuery = parseOptionalWorldQuery(body.worldQuery, issues);

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
        ["VILLAGE", "DESERT"] as const,
        issues
    ) as WorldQueryTarget;
    const status = readEnum(
        query.status,
        "worldQuery.status",
        ["FOUND", "NOT_FOUND", "UNSUPPORTED"] as const,
        issues
    ) as WorldQueryStatus;
    const dimension = readString(query.dimension, "worldQuery.dimension", issues);

    let position: WorldQueryPosition | undefined;
    let distanceBlocks: number | undefined;

    if (status === "FOUND") {
        const rawPosition = readRecord(query.position, "worldQuery.position", issues);
        position = {
            x: readInteger(rawPosition.x, "worldQuery.position.x", issues),
            z: readInteger(rawPosition.z, "worldQuery.position.z", issues)
        };
        distanceBlocks = readNonNegativeInteger(
            query.distanceBlocks,
            "worldQuery.distanceBlocks",
            issues
        );
    }

    const reason = readOptionalString(query.reason, "worldQuery.reason", issues);

    return {
        kind,
        target,
        status,
        dimension,
        ...(position ? { position } : {}),
        ...(distanceBlocks !== undefined ? { distanceBlocks } : {}),
        ...(reason ? { reason } : {})
    };
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

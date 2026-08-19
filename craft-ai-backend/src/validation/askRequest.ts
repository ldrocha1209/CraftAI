import type {
    AssistanceMode,
    AskRequest,
    ConversationContext,
    ConversationTurn,
    MatchedItem,
    MinecraftRecipe,
    NavigationDirection,
    PlayerContext,
    PlayerEquipment,
    PlayerPosition,
    ReferencedDestination,
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
    const assistanceMode = readEnum(
        body.assistanceMode,
        "assistanceMode",
        ["GENERAL", "RECOMMENDATION", "GOAL_PLAN"] as const,
        issues
    ) as AssistanceMode;
    const player = parsePlayerContext(body.player, issues);
    const matchedItem = parseOptionalMatchedItem(body.matchedItem, issues);
    const recipe = parseOptionalRecipe(
        body.recipe,
        matchedItem,
        player.inventory,
        issues
    );
    const worldQuery = parseOptionalWorldQuery(body.worldQuery, player.position, issues);
    const conversation = parseConversationContext(body.conversation, player, issues);

    if (issues.length > 0) {
        throw new RequestValidationError(issues);
    }

    return {
        question,
        assistanceMode,
        player,
        ...(matchedItem ? { matchedItem } : {}),
        ...(recipe ? { recipe } : {}),
        ...(worldQuery ? { worldQuery } : {}),
        conversation
    };
}

function parseConversationContext(
    value: unknown,
    player: PlayerContext,
    issues: string[]
): ConversationContext {
    const conversation = readRecord(value, "conversation", issues);
    const followUp = readBoolean(conversation.followUp, "conversation.followUp", issues);
    const recentTurns = readArray(
        conversation.recentTurns,
        "conversation.recentTurns",
        issues
    ).map((turn, index) => parseConversationTurn(turn, index, issues));

    if (recentTurns.length > 5) {
        issues.push("conversation.recentTurns must contain at most 5 turns.");
    }

    const lastDestination = parseOptionalReferencedDestination(
        conversation.lastDestination,
        player,
        issues
    );

    if (!followUp && (recentTurns.length > 0 || lastDestination)) {
        issues.push("Non-follow-up requests must not include conversation history.");
    }
    if (followUp && recentTurns.length === 0) {
        issues.push("A follow-up request requires at least one recent turn.");
    }

    return {
        followUp,
        recentTurns,
        ...(lastDestination ? { lastDestination } : {})
    };
}

function parseConversationTurn(
    value: unknown,
    index: number,
    issues: string[]
): ConversationTurn {
    const path = `conversation.recentTurns[${index}]`;
    const turn = readRecord(value, path, issues);
    return {
        question: readBoundedString(turn.question, `${path}.question`, 300, issues),
        answer: readBoundedString(turn.answer, `${path}.answer`, 1_200, issues)
    };
}

function parseOptionalReferencedDestination(
    value: unknown,
    player: PlayerContext,
    issues: string[]
): ReferencedDestination | undefined {
    if (value === undefined || value === null) {
        return undefined;
    }

    const destination = readRecord(value, "conversation.lastDestination", issues);
    const kind = readEnum(
        destination.kind,
        "conversation.lastDestination.kind",
        ["STRUCTURE", "BIOME"] as const,
        issues
    ) as WorldQueryKind;
    const target = readEnum(
        destination.target,
        "conversation.lastDestination.target",
        WORLD_QUERY_TARGETS,
        issues
    ) as WorldQueryTarget;
    const dimension = readString(
        destination.dimension,
        "conversation.lastDestination.dimension",
        issues
    );
    const rawPosition = readRecord(
        destination.position,
        "conversation.lastDestination.position",
        issues
    );
    const position: WorldQueryPosition = {
        x: readInteger(rawPosition.x, "conversation.lastDestination.position.x", issues),
        z: readInteger(rawPosition.z, "conversation.lastDestination.position.z", issues)
    };
    const ageSeconds = readNonNegativeInteger(
        destination.ageSeconds,
        "conversation.lastDestination.ageSeconds",
        issues
    );
    const sameDimension = readBoolean(
        destination.sameDimension,
        "conversation.lastDestination.sameDimension",
        issues
    );

    if (kind !== worldQueryKindForTarget(target)) {
        issues.push(
            `conversation.lastDestination.kind must be ${worldQueryKindForTarget(target)} `
            + `for target ${target}.`
        );
    }
    if (ageSeconds > 600) {
        issues.push("conversation.lastDestination.ageSeconds must not exceed 600.");
    }

    const expectedSameDimension = dimension === player.dimension;
    if (sameDimension !== expectedSameDimension) {
        issues.push(
            "conversation.lastDestination.sameDimension does not match the supplied dimensions."
        );
    }

    let navigation: WorldQueryNavigation | undefined;
    if (destination.navigation !== undefined && destination.navigation !== null) {
        navigation = parseWorldQueryNavigation(destination.navigation, issues);
        validateNavigationConsistency(
            position,
            navigation,
            player.position,
            "conversation.lastDestination.navigation",
            issues
        );
    }
    if (!sameDimension && navigation) {
        issues.push(
            "conversation.lastDestination.navigation is not allowed across dimensions."
        );
    }
    if (sameDimension && player.position && !navigation) {
        issues.push(
            "conversation.lastDestination.navigation is required in the player's dimension."
        );
    }

    return {
        sourceQuestion: readBoundedString(
            destination.sourceQuestion,
            "conversation.lastDestination.sourceQuestion",
            300,
            issues
        ),
        kind,
        target,
        dimension,
        position,
        ...(navigation ? { navigation } : {}),
        ageSeconds,
        sameDimension
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
    matchedItem: MatchedItem | undefined,
    inventory: Record<string, number>,
    issues: string[]
): MinecraftRecipe | undefined {
    if (value === undefined || value === null) {
        return undefined;
    }

    const recipe = readRecord(value, "recipe", issues);
    const output = readRecord(recipe.output, "recipe.output", issues);
    const parsedOutput = {
        itemId: readString(output.itemId, "recipe.output.itemId", issues),
        count: readPositiveInteger(output.count, "recipe.output.count", issues)
    };
    const requirements = readArray(
        recipe.requirements,
        "recipe.requirements",
        issues
    ).map((requirement, index) =>
        parseRecipeRequirement(requirement, index, issues)
    );
    const craftable = readBoolean(recipe.craftable, "recipe.craftable", issues);
    const totalMissing = readNonNegativeInteger(
        recipe.totalMissing,
        "recipe.totalMissing",
        issues
    );

    validateRecipeConsistency(
        matchedItem,
        inventory,
        parsedOutput,
        requirements,
        craftable,
        totalMissing,
        issues
    );

    return {
        recipeId: readString(recipe.recipeId, "recipe.recipeId", issues),
        type: readEnum(
            recipe.type,
            "recipe.type",
            ["SHAPED", "SHAPELESS"] as const,
            issues
        ),
        output: parsedOutput,
        requirements,
        craftable,
        totalMissing
    };
}

function parseRecipeRequirement(
    value: unknown,
    index: number,
    issues: string[]
): MinecraftRecipe["requirements"][number] {
    const path = `recipe.requirements[${index}]`;
    const requirement = readRecord(value, path, issues);
    return {
        alternatives: readStringArray(
            requirement.alternatives,
            `${path}.alternatives`,
            issues,
            true
        ),
        tags: readStringArray(requirement.tags, `${path}.tags`, issues),
        requiredCount: readPositiveInteger(
            requirement.requiredCount,
            `${path}.requiredCount`,
            issues
        ),
        availableCount: readNonNegativeInteger(
            requirement.availableCount,
            `${path}.availableCount`,
            issues
        ),
        availableItems: readCountMap(
            requirement.availableItems,
            `${path}.availableItems`,
            issues
        ),
        missingCount: readNonNegativeInteger(
            requirement.missingCount,
            `${path}.missingCount`,
            issues
        )
    };
}

function validateRecipeConsistency(
    matchedItem: MatchedItem | undefined,
    inventory: Record<string, number>,
    output: MinecraftRecipe["output"],
    requirements: MinecraftRecipe["requirements"],
    craftable: boolean,
    totalMissing: number,
    issues: string[]
): void {
    if (!matchedItem) {
        issues.push("recipe requires matchedItem context.");
    } else if (output.itemId !== matchedItem.id) {
        issues.push("recipe.output.itemId must match matchedItem.id.");
    }
    if (requirements.length === 0) {
        issues.push("recipe.requirements must contain at least one requirement.");
    }

    const allocatedInventory: Record<string, number> = {};
    let calculatedMissing = 0;
    requirements.forEach((requirement, index) => {
        const path = `recipe.requirements[${index}]`;
        const alternatives = new Set(requirement.alternatives);
        if (alternatives.size !== requirement.alternatives.length) {
            issues.push(`${path}.alternatives must not contain duplicates.`);
        }
        const availableItemTotal = Object.values(requirement.availableItems)
            .reduce((sum, count) => sum + count, 0);
        if (availableItemTotal !== requirement.availableCount) {
            issues.push(`${path}.availableCount must equal availableItems total.`);
        }
        if (requirement.availableCount + requirement.missingCount
            !== requirement.requiredCount) {
            issues.push(`${path} available and missing counts must equal requiredCount.`);
        }
        for (const [itemId, count] of Object.entries(requirement.availableItems)) {
            if (!alternatives.has(itemId)) {
                issues.push(`${path}.availableItems contains a non-alternative item.`);
            }
            allocatedInventory[itemId] = (allocatedInventory[itemId] ?? 0) + count;
        }
        calculatedMissing += requirement.missingCount;
    });

    for (const [itemId, allocated] of Object.entries(allocatedInventory)) {
        if (allocated > (inventory[itemId] ?? 0)) {
            issues.push(`recipe allocates more ${itemId} than the player inventory contains.`);
        }
    }
    if (calculatedMissing !== totalMissing) {
        issues.push("recipe.totalMissing must equal the requirement missing total.");
    }
    if (craftable !== (totalMissing === 0)) {
        issues.push("recipe.craftable must be true exactly when totalMissing is zero.");
    }
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
        validateNavigationConsistency(
            position,
            navigation,
            playerPosition,
            "worldQuery.navigation",
            issues
        );
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
    path: string,
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
        issues.push(`${path}.deltaXBlocks does not match the supplied positions.`);
    }
    if (navigation.deltaZBlocks !== expectedDeltaZ) {
        issues.push(`${path}.deltaZBlocks does not match the supplied positions.`);
    }
    if (navigation.distanceBlocks !== expectedDistance) {
        issues.push(`${path}.distanceBlocks does not match the supplied positions.`);
    }
    if (navigation.direction !== expectedDirection) {
        issues.push(`${path}.direction does not match the supplied positions.`);
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

function readBoundedString(
    value: unknown,
    path: string,
    maxLength: number,
    issues: string[]
): string {
    const string = readString(value, path, issues, true);
    if (string.length > maxLength) {
        issues.push(`${path} must not exceed ${maxLength} characters.`);
    }
    return string;
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

function readBoolean(value: unknown, path: string, issues: string[]): boolean {
    if (typeof value !== "boolean") {
        issues.push(`${path} must be a boolean.`);
        return false;
    }

    return value;
}

function readArray(value: unknown, path: string, issues: string[]): unknown[] {
    if (!Array.isArray(value)) {
        issues.push(`${path} must be an array.`);
        return [];
    }

    return value;
}

function readStringArray(
    value: unknown,
    path: string,
    issues: string[],
    requireNonEmpty = false
): string[] {
    const values = readArray(value, path, issues);
    const strings = values.map((entry, index) =>
        readString(entry, `${path}[${index}]`, issues, true)
    );

    if (requireNonEmpty && strings.length === 0) {
        issues.push(`${path} must contain at least one item ID.`);
    }

    return strings;
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

function readPositiveInteger(
    value: unknown,
    path: string,
    issues: string[]
): number {
    const number = readInteger(value, path, issues);

    if (number <= 0) {
        issues.push(`${path} must be positive.`);
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

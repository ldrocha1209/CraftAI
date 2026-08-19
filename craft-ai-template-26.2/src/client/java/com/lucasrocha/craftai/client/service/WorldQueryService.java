package com.lucasrocha.craftai.client.service;

import com.lucasrocha.craftai.client.data.WorldQueryResult;
import com.lucasrocha.craftai.client.data.MinecraftResourceNames;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class WorldQueryService {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Minecraft interprets structure-search radii in chunks, not blocks.
    private static final int VILLAGE_SEARCH_RADIUS_CHUNKS = 100;
    private static final boolean SKIP_KNOWN_STRUCTURES = false;

    // Biome searches use block radii. Larger sampling intervals reduce server-thread work
    // at the cost of returning an approximate point within the target biome.
    private static final int DESERT_SEARCH_RADIUS_BLOCKS = 6400;
    private static final int DESERT_HORIZONTAL_INTERVAL_BLOCKS = 128;
    private static final int DESERT_VERTICAL_INTERVAL_BLOCKS = 64;

    private WorldQueryService() {}

    public static CompletableFuture<WorldQueryResult> findNearestAsync(
            MinecraftServer server,
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryResult.Target target
    ) {
        if (target == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (server == null || serverLevel == null || playerPosition == null) {
            return CompletableFuture.completedFuture(unsupported(target, serverLevel));
        }

        return switch (target) {
            case VILLAGE -> findNearestStructureAsync(
                    server,
                    serverLevel,
                    playerPosition,
                    target,
                    StructureTags.VILLAGE,
                    VILLAGE_SEARCH_RADIUS_CHUNKS,
                    SKIP_KNOWN_STRUCTURES
            );
            case DESERT -> findNearestBiomeAsync(
                    server,
                    serverLevel,
                    playerPosition,
                    target,
                    biomeHolder -> biomeHolder.is(Biomes.DESERT),
                    DESERT_SEARCH_RADIUS_BLOCKS,
                    DESERT_HORIZONTAL_INTERVAL_BLOCKS,
                    DESERT_VERTICAL_INTERVAL_BLOCKS
            );
        };
    }

    public static WorldQueryResult findNearestStructure(
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryResult.Target target,
            TagKey<Structure> structureTag,
            int searchRadiusChunks,
            boolean skipKnownStructures
    ) {
        requireSearchInputs(serverLevel, playerPosition, target);
        if (target.getKind() != WorldQueryResult.Kind.STRUCTURE) {
            throw new IllegalArgumentException("A structure search requires a STRUCTURE target.");
        }
        if (structureTag == null || searchRadiusChunks <= 0) {
            throw new IllegalArgumentException("A structure tag and positive search radius are required.");
        }

        BlockPos resultPosition = serverLevel.findNearestMapStructure(
                structureTag,
                playerPosition,
                searchRadiusChunks,
                skipKnownStructures
        );

        if (resultPosition == null) {
            return WorldQueryResult.notFound(
                    target.getKind(),
                    target,
                    MinecraftResourceNames.dimensionId(serverLevel)
            );
        }

        return foundResult(serverLevel, playerPosition, resultPosition, target);
    }

    public static CompletableFuture<WorldQueryResult> findNearestStructureAsync(
            MinecraftServer server,
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryResult.Target target,
            TagKey<Structure> structureTag,
            int searchRadiusChunks,
            boolean skipKnownStructures
    ) {
        return runOnServerThread(
                server,
                target,
                () -> findNearestStructure(
                        serverLevel,
                        playerPosition,
                        target,
                        structureTag,
                        searchRadiusChunks,
                        skipKnownStructures
                )
        );
    }

    public static WorldQueryResult findNearestBiome(
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryResult.Target target,
            Predicate<Holder<Biome>> biomePredicate,
            int searchRadiusBlocks,
            int horizontalIntervalBlocks,
            int verticalIntervalBlocks
    ) {
        requireSearchInputs(serverLevel, playerPosition, target);
        if (target.getKind() != WorldQueryResult.Kind.BIOME) {
            throw new IllegalArgumentException("A biome search requires a BIOME target.");
        }
        if (biomePredicate == null
                || searchRadiusBlocks <= 0
                || horizontalIntervalBlocks <= 0
                || verticalIntervalBlocks <= 0) {
            throw new IllegalArgumentException("A biome predicate and positive search settings are required.");
        }

        var result = serverLevel.findClosestBiome3d(
                biomePredicate,
                playerPosition,
                searchRadiusBlocks,
                horizontalIntervalBlocks,
                verticalIntervalBlocks
        );

        if (result == null) {
            return WorldQueryResult.notFound(
                    target.getKind(),
                    target,
                    MinecraftResourceNames.dimensionId(serverLevel)
            );
        }

        return foundResult(serverLevel, playerPosition, result.getFirst(), target);
    }

    public static CompletableFuture<WorldQueryResult> findNearestBiomeAsync(
            MinecraftServer server,
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryResult.Target target,
            Predicate<Holder<Biome>> biomePredicate,
            int searchRadiusBlocks,
            int horizontalIntervalBlocks,
            int verticalIntervalBlocks
    ) {
        return runOnServerThread(
                server,
                target,
                () -> findNearestBiome(
                        serverLevel,
                        playerPosition,
                        target,
                        biomePredicate,
                        searchRadiusBlocks,
                        horizontalIntervalBlocks,
                        verticalIntervalBlocks
                )
        );
    }

    private static CompletableFuture<WorldQueryResult> runOnServerThread(
            MinecraftServer server,
            WorldQueryResult.Target target,
            Supplier<WorldQueryResult> search
    ) {
        CompletableFuture<WorldQueryResult> future = new CompletableFuture<>();

        if (server == null) {
            future.completeExceptionally(
                    new IllegalArgumentException("An IntegratedServer is required for world searches.")
            );
            return future;
        }

        server.execute(() -> {
            long startedAt = System.nanoTime();
            LOGGER.info("CraftAI: Running {} search on server thread", target.name().toLowerCase());

            try {
                WorldQueryResult result = search.get();
                double durationSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
                LOGGER.info(
                        "CraftAI: {} search completed in {} seconds",
                        target.name().toLowerCase(),
                        String.format("%.2f", durationSeconds)
                );
                future.complete(result);
            } catch (Exception error) {
                LOGGER.error("CraftAI: {} search failed", target.name().toLowerCase(), error);
                future.completeExceptionally(error);
            }
        });

        return future;
    }

    private static WorldQueryResult foundResult(
            ServerLevel serverLevel,
            BlockPos playerPosition,
            BlockPos resultPosition,
            WorldQueryResult.Target target
    ) {
        int distance = (int) Math.round(Math.sqrt(playerPosition.distSqr(resultPosition)));

        return WorldQueryResult.found(
                target.getKind(),
                target,
                MinecraftResourceNames.dimensionId(serverLevel),
                resultPosition.getX(),
                resultPosition.getZ(),
                distance
        );
    }

    private static WorldQueryResult unsupported(
            WorldQueryResult.Target target,
            ServerLevel serverLevel
    ) {
        return WorldQueryResult.unsupported(
                target.getKind(),
                target,
                MinecraftResourceNames.dimensionId(serverLevel),
                "A single-player IntegratedServer, server level, and player position are required."
        );
    }

    private static void requireSearchInputs(
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryResult.Target target
    ) {
        if (serverLevel == null || playerPosition == null || target == null) {
            throw new IllegalArgumentException("A server level, player position, and target are required.");
        }
    }

}

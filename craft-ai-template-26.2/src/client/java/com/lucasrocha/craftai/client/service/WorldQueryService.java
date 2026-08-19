package com.lucasrocha.craftai.client.service;

import com.lucasrocha.craftai.client.data.MinecraftResourceNames;
import com.lucasrocha.craftai.client.data.WorldQueryResult;
import com.lucasrocha.craftai.client.data.WorldQueryTarget;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class WorldQueryService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean SKIP_KNOWN_STRUCTURES = false;

    private static final int OVERWORLD_BIOME_RADIUS_BLOCKS = 6400;
    private static final int OVERWORLD_BIOME_HORIZONTAL_INTERVAL_BLOCKS = 256;
    private static final int OVERWORLD_BIOME_VERTICAL_INTERVAL_BLOCKS = 64;
    private static final int NETHER_BIOME_RADIUS_BLOCKS = 3200;
    private static final int NETHER_BIOME_HORIZONTAL_INTERVAL_BLOCKS = 128;
    private static final int NETHER_BIOME_VERTICAL_INTERVAL_BLOCKS = 32;
    private static final int END_BIOME_RADIUS_BLOCKS = 6400;
    private static final int END_BIOME_HORIZONTAL_INTERVAL_BLOCKS = 256;
    private static final int END_BIOME_VERTICAL_INTERVAL_BLOCKS = 64;

    private static final long CACHE_TTL_NANOS = Duration.ofMinutes(5).toNanos();
    private static final int CACHE_MAX_PLAYER_MOVEMENT_BLOCKS = 256;
    private static final Map<MinecraftServer, Map<CacheKey, CachedResult>> CACHE =
            new WeakHashMap<>();

    private WorldQueryService() {}

    public static CompletableFuture<WorldQueryResult> findNearestAsync(
            MinecraftServer server,
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryTarget target
    ) {
        if (target == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (server == null || serverLevel == null || playerPosition == null) {
            return CompletableFuture.completedFuture(unsupported(
                    target,
                    serverLevel,
                    "A single-player IntegratedServer, server level, and player position are required."
            ));
        }

        WorldQueryResult cachedResult = findCached(
                server,
                MinecraftResourceNames.dimensionId(serverLevel),
                playerPosition,
                target
        );
        if (cachedResult != null) {
            return CompletableFuture.completedFuture(cachedResult);
        }

        CompletableFuture<WorldQueryResult> searchFuture = switch (target.kind()) {
            case STRUCTURE -> runOnServerThread(
                    server,
                    target,
                    () -> findNearestStructure(serverLevel, playerPosition, target)
            );
            case BIOME -> runOnServerThread(
                    server,
                    target,
                    () -> findNearestBiome(serverLevel, playerPosition, target)
            );
        };

        return searchFuture.thenApply(result -> {
            cacheResult(server, playerPosition, target, result);
            return result;
        });
    }

    public static WorldQueryResult findNearestStructure(
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryTarget target
    ) {
        requireSearchInputs(serverLevel, playerPosition, target);
        if (target.kind() != WorldQueryResult.Kind.STRUCTURE) {
            throw new IllegalArgumentException("A structure search requires a STRUCTURE target.");
        }

        Registry<Structure> structureRegistry = serverLevel.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> registeredStructures = new ArrayList<>();

        for (String registryId : target.registryIds()) {
            structureRegistry.get(Identifier.parse(registryId))
                    .ifPresent(registeredStructures::add);
        }

        if (registeredStructures.size() != target.registryIds().size()) {
            return unsupported(
                    target,
                    serverLevel,
                    "One or more configured structure registry IDs are unavailable."
            );
        }

        List<Holder<Structure>> dimensionStructures = registeredStructures.stream()
                .filter(structure -> structure.value().biomes().stream()
                        .anyMatch(biome -> biomeSupportsDimension(biome, serverLevel.dimension())))
                .toList();

        if (dimensionStructures.isEmpty()) {
            return unsupported(
                    target,
                    serverLevel,
                    target.displayName() + " does not generate in the current dimension."
            );
        }

        LOGGER.info(
                "CraftAI: {} structure placement search radius={}",
                target.identifier(),
                target.structureSearchRadius()
        );
        var result = serverLevel.getChunkSource()
                .getGenerator()
                .findNearestMapStructure(
                        serverLevel,
                        HolderSet.direct(dimensionStructures),
                        playerPosition,
                        target.structureSearchRadius(),
                        SKIP_KNOWN_STRUCTURES
                );

        if (result == null) {
            return WorldQueryResult.notFound(
                    target.kind(),
                    target,
                    MinecraftResourceNames.dimensionId(serverLevel)
            );
        }

        return foundResult(serverLevel, playerPosition, result.getFirst(), target);
    }

    public static WorldQueryResult findNearestBiome(
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryTarget target
    ) {
        requireSearchInputs(serverLevel, playerPosition, target);
        if (target.kind() != WorldQueryResult.Kind.BIOME) {
            throw new IllegalArgumentException("A biome search requires a BIOME target.");
        }

        Registry<Biome> biomeRegistry = serverLevel.registryAccess().lookupOrThrow(Registries.BIOME);
        Identifier targetBiomeId = Identifier.parse(target.registryIds().getFirst());
        Holder<Biome> targetBiome = biomeRegistry.get(targetBiomeId)
                .orElse(null);

        if (targetBiome == null) {
            return unsupported(target, serverLevel, "The configured biome registry ID is unavailable.");
        }

        if (!biomeSupportsDimension(targetBiome, serverLevel.dimension())) {
            return unsupported(
                    target,
                    serverLevel,
                    target.displayName() + " does not generate in the current dimension."
            );
        }

        BiomeSearchSettings settings = biomeSearchSettings(serverLevel.dimension());
        LOGGER.info(
                "CraftAI: {} biome bounds radiusBlocks={}, horizontalIntervalBlocks={}",
                target.identifier(),
                settings.radiusBlocks(),
                settings.horizontalIntervalBlocks()
        );
        var result = serverLevel.findClosestBiome3d(
                biome -> biome.is(targetBiomeId),
                playerPosition,
                settings.radiusBlocks(),
                settings.horizontalIntervalBlocks(),
                settings.verticalIntervalBlocks()
        );

        if (result == null) {
            return WorldQueryResult.notFound(
                    target.kind(),
                    target,
                    MinecraftResourceNames.dimensionId(serverLevel)
            );
        }

        return foundResult(serverLevel, playerPosition, result.getFirst(), target);
    }

    private static CompletableFuture<WorldQueryResult> runOnServerThread(
            MinecraftServer server,
            WorldQueryTarget target,
            Supplier<WorldQueryResult> search
    ) {
        CompletableFuture<WorldQueryResult> future = new CompletableFuture<>();

        server.execute(() -> {
            long startedAt = System.nanoTime();
            LOGGER.info(
                    "CraftAI: Running {} search on server thread",
                    target.identifier()
            );

            try {
                WorldQueryResult result = search.get();
                double durationSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
                LOGGER.info(
                        "CraftAI: {} search completed in {} seconds",
                        target.identifier(),
                        String.format(java.util.Locale.ROOT, "%.2f", durationSeconds)
                );
                future.complete(result);
            } catch (Exception error) {
                LOGGER.error("CraftAI: {} search failed", target.identifier(), error);
                future.completeExceptionally(error);
            }
        });

        return future;
    }

    private static boolean biomeSupportsDimension(
            Holder<Biome> biome,
            ResourceKey<Level> dimension
    ) {
        if (Level.OVERWORLD.equals(dimension)) {
            return biome.is(BiomeTags.IS_OVERWORLD);
        }
        if (Level.NETHER.equals(dimension)) {
            return biome.is(BiomeTags.IS_NETHER);
        }
        if (Level.END.equals(dimension)) {
            return biome.is(BiomeTags.IS_END);
        }
        return false;
    }

    private static BiomeSearchSettings biomeSearchSettings(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return new BiomeSearchSettings(
                    NETHER_BIOME_RADIUS_BLOCKS,
                    NETHER_BIOME_HORIZONTAL_INTERVAL_BLOCKS,
                    NETHER_BIOME_VERTICAL_INTERVAL_BLOCKS
            );
        }
        if (Level.END.equals(dimension)) {
            return new BiomeSearchSettings(
                    END_BIOME_RADIUS_BLOCKS,
                    END_BIOME_HORIZONTAL_INTERVAL_BLOCKS,
                    END_BIOME_VERTICAL_INTERVAL_BLOCKS
            );
        }
        return new BiomeSearchSettings(
                OVERWORLD_BIOME_RADIUS_BLOCKS,
                OVERWORLD_BIOME_HORIZONTAL_INTERVAL_BLOCKS,
                OVERWORLD_BIOME_VERTICAL_INTERVAL_BLOCKS
        );
    }

    private static WorldQueryResult foundResult(
            ServerLevel serverLevel,
            BlockPos playerPosition,
            BlockPos resultPosition,
            WorldQueryTarget target
    ) {
        return WorldQueryResult.found(
                target.kind(),
                target,
                MinecraftResourceNames.dimensionId(serverLevel),
                resultPosition.getX(),
                resultPosition.getZ(),
                0
        ).withDistanceFrom(playerPosition.getX(), playerPosition.getZ());
    }

    private static WorldQueryResult unsupported(
            WorldQueryTarget target,
            ServerLevel serverLevel,
            String reason
    ) {
        return WorldQueryResult.unsupported(
                target.kind(),
                target,
                MinecraftResourceNames.dimensionId(serverLevel),
                reason
        );
    }

    private static void requireSearchInputs(
            ServerLevel serverLevel,
            BlockPos playerPosition,
            WorldQueryTarget target
    ) {
        if (serverLevel == null || playerPosition == null || target == null) {
            throw new IllegalArgumentException("A server level, player position, and target are required.");
        }
    }

    private static WorldQueryResult findCached(
            MinecraftServer server,
            String dimension,
            BlockPos playerPosition,
            WorldQueryTarget target
    ) {
        synchronized (CACHE) {
            Map<CacheKey, CachedResult> serverCache = CACHE.get(server);
            if (serverCache == null) {
                return null;
            }

            CacheKey key = new CacheKey(dimension, target.identifier());
            CachedResult cached = serverCache.get(key);
            if (cached == null) {
                return null;
            }

            long deltaX = cached.origin().getX() - playerPosition.getX();
            long deltaZ = cached.origin().getZ() - playerPosition.getZ();
            long movementSquared = deltaX * deltaX + deltaZ * deltaZ;
            boolean expired = System.nanoTime() >= cached.expiresAtNanos();
            boolean movedTooFar = movementSquared
                    > (long) CACHE_MAX_PLAYER_MOVEMENT_BLOCKS * CACHE_MAX_PLAYER_MOVEMENT_BLOCKS;

            if (expired || movedTooFar) {
                serverCache.remove(key);
                return null;
            }

            LOGGER.info("CraftAI: Reusing cached {} result", target.identifier());
            return cached.result().withDistanceFrom(playerPosition.getX(), playerPosition.getZ());
        }
    }

    private static void cacheResult(
            MinecraftServer server,
            BlockPos playerPosition,
            WorldQueryTarget target,
            WorldQueryResult result
    ) {
        if (!result.isReusable()) {
            return;
        }

        synchronized (CACHE) {
            Map<CacheKey, CachedResult> serverCache = CACHE.computeIfAbsent(
                    server,
                    ignored -> new HashMap<>()
            );
            serverCache.put(
                    new CacheKey(result.dimension(), target.identifier()),
                    new CachedResult(
                            result,
                            playerPosition.immutable(),
                            System.nanoTime() + CACHE_TTL_NANOS
                    )
            );
        }
    }

    private record BiomeSearchSettings(
            int radiusBlocks,
            int horizontalIntervalBlocks,
            int verticalIntervalBlocks
    ) {}

    private record CacheKey(String dimension, String target) {}

    private record CachedResult(
            WorldQueryResult result,
            BlockPos origin,
            long expiresAtNanos
    ) {}
}

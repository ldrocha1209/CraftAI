package com.lucasrocha.craftai.client.service;

import com.lucasrocha.craftai.client.data.WorldQueryResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import java.util.concurrent.CompletableFuture;

import java.util.function.Predicate;

public class WorldQueryService {

    private static final int DESERT_SEARCH_RADIUS_BLOCKS = 6400;
    private static final int DESERT_HORIZONTAL_INTERVAL_BLOCKS = 128;
    private static final int DESERT_VERTICAL_INTERVAL_BLOCKS = 64;

    public static WorldQueryResult findNearestVillage(
            MinecraftServer server,
            BlockPos playerPosition
    ) {

        if (server == null || playerPosition == null) {
            return WorldQueryResult.unsupported(
                    WorldQueryResult.Kind.STRUCTURE,
                    WorldQueryResult.Target.VILLAGE,
                    "UNKNOWN",
                    "A single-player IntegratedServer and player position are required."
            );
        }

        ServerLevel serverLevel = server.overworld();
        String dimension = serverLevel.dimension().identifier().toString();

        BlockPos villagePos = serverLevel.findNearestMapStructure(
                StructureTags.VILLAGE,
                playerPosition,
                100,
                false
        );

        if (villagePos == null) {
            return WorldQueryResult.notFound(
                    WorldQueryResult.Kind.STRUCTURE,
                    WorldQueryResult.Target.VILLAGE,
                    dimension
            );
        }

        int distance = (int) Math.round(
                Math.sqrt(playerPosition.distSqr(villagePos))
        );

        return WorldQueryResult.found(
                WorldQueryResult.Kind.STRUCTURE,
                WorldQueryResult.Target.VILLAGE,
                dimension,
                villagePos.getX(),
                villagePos.getZ(),
                distance
        );
    }

    public static CompletableFuture<WorldQueryResult> findNearestVillageAsync(
            MinecraftServer server,
            BlockPos playerPosition
    ) {

        CompletableFuture<WorldQueryResult> future =
                new CompletableFuture<>();

        if (server == null || playerPosition == null) {
            future.complete(findNearestVillage(server, playerPosition));
            return future;
        }

        server.execute(() -> {

            try {

                System.out.println(
                        "CraftAI: Running village search on server thread"
                );

                WorldQueryResult village =
                        findNearestVillage(
                                server,
                                playerPosition
                        );

                future.complete(village);

            } catch (Exception e) {

                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public static WorldQueryResult findNearestDesert(
            MinecraftServer server,
            BlockPos playerPosition
    ) {

        if (server == null || playerPosition == null) {
            return WorldQueryResult.unsupported(
                    WorldQueryResult.Kind.BIOME,
                    WorldQueryResult.Target.DESERT,
                    "UNKNOWN",
                    "A single-player IntegratedServer and player position are required."
            );
        }

        ServerLevel serverLevel = server.overworld();
        String dimension = serverLevel.dimension().identifier().toString();

        Predicate<Holder<Biome>> desertPredicate =
                biomeHolder -> biomeHolder.is(Biomes.DESERT);

        var desertResult = serverLevel.findClosestBiome3d(
                desertPredicate,
                playerPosition,
                DESERT_SEARCH_RADIUS_BLOCKS,
                DESERT_HORIZONTAL_INTERVAL_BLOCKS,
                DESERT_VERTICAL_INTERVAL_BLOCKS
        );

        if (desertResult == null) {
            return WorldQueryResult.notFound(
                    WorldQueryResult.Kind.BIOME,
                    WorldQueryResult.Target.DESERT,
                    dimension
            );
        }

        BlockPos desertPos = desertResult.getFirst();

        int distance = (int) Math.round(
                Math.sqrt(playerPosition.distSqr(desertPos))
        );

        return WorldQueryResult.found(
                WorldQueryResult.Kind.BIOME,
                WorldQueryResult.Target.DESERT,
                dimension,
                desertPos.getX(),
                desertPos.getZ(),
                distance
        );
    }

    public static CompletableFuture<WorldQueryResult> findNearestDesertAsync(
            MinecraftServer server,
            BlockPos playerPosition
    ) {

        CompletableFuture<WorldQueryResult> future =
                new CompletableFuture<>();

        if (server == null || playerPosition == null) {
            future.complete(findNearestDesert(server, playerPosition));
            return future;
        }

        server.execute(() -> {

            try {

                long startedAt = System.nanoTime();

                System.out.println(
                        "CraftAI: Running desert search on server thread"
                );

                WorldQueryResult desert =
                        findNearestDesert(
                                server,
                                playerPosition
                        );

                double durationSeconds =
                        (System.nanoTime() - startedAt) / 1_000_000_000.0;

                System.out.printf(
                        "CraftAI: Desert search completed in %.2f seconds%n",
                        durationSeconds
                );

                future.complete(desert);

            } catch (Exception e) {

                future.completeExceptionally(e);
            }
        });

        return future;
    }
}

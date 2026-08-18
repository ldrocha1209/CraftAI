package com.lucasrocha.craftai.client.service;

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

    public record WorldLocation(
            String type,
            int x,
            int z,
            int distance
    ) {}

    public static WorldLocation findNearestVillage(
            MinecraftServer server,
            BlockPos playerPosition
    ) {

        if (server == null || playerPosition == null) {
            return null;
        }

        ServerLevel serverLevel = server.overworld();

        BlockPos villagePos = serverLevel.findNearestMapStructure(
                StructureTags.VILLAGE,
                playerPosition,
                100,
                false
        );

        if (villagePos == null) {
            return null;
        }

        int distance = (int) Math.round(
                Math.sqrt(playerPosition.distSqr(villagePos))
        );

        return new WorldLocation(
                "Village",
                villagePos.getX(),
                villagePos.getZ(),
                distance
        );
    }

    public static CompletableFuture<WorldLocation> findNearestVillageAsync(
            MinecraftServer server,
            BlockPos playerPosition
    ) {

        CompletableFuture<WorldLocation> future =
                new CompletableFuture<>();

        if (server == null || playerPosition == null) {
            future.complete(null);
            return future;
        }

        server.execute(() -> {

            try {

                System.out.println(
                        "CraftAI: Running village search on server thread"
                );

                WorldLocation village =
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

    public static WorldLocation findNearestDesert(
            MinecraftServer server,
            BlockPos playerPosition
    ) {

        if (server == null || playerPosition == null) {
            return null;
        }

        ServerLevel serverLevel = server.overworld();

        Predicate<Holder<Biome>> desertPredicate =
                biomeHolder -> biomeHolder.is(Biomes.DESERT);

        var desertResult = serverLevel.findClosestBiome3d(
                desertPredicate,
                playerPosition,
                6400,
                32,
                32
        );

        if (desertResult == null) {
            return null;
        }

        BlockPos desertPos = desertResult.getFirst();

        int distance = (int) Math.round(
                Math.sqrt(playerPosition.distSqr(desertPos))
        );

        return new WorldLocation(
                "Desert",
                desertPos.getX(),
                desertPos.getZ(),
                distance
        );
    }

    public static CompletableFuture<WorldLocation> findNearestDesertAsync(
            MinecraftServer server,
            BlockPos playerPosition
    ) {

        CompletableFuture<WorldLocation> future =
                new CompletableFuture<>();

        if (server == null || playerPosition == null) {
            future.complete(null);
            return future;
        }

        server.execute(() -> {

            try {

                System.out.println(
                        "CraftAI: Running desert search on server thread"
                );

                WorldLocation desert =
                        findNearestDesert(
                                server,
                                playerPosition
                        );

                future.complete(desert);

            } catch (Exception e) {

                future.completeExceptionally(e);
            }
        });

        return future;
    }
}
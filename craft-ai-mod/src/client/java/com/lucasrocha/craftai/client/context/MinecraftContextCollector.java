package com.lucasrocha.craftai.client.context;

import com.lucasrocha.craftai.client.data.MinecraftResourceNames;
import com.lucasrocha.craftai.client.data.PlayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class MinecraftContextCollector {

    private static final String UNKNOWN = "UNKNOWN";
    private static final String EMPTY = "EMPTY";

    private MinecraftContextCollector() {}

    public static PlayerContext collect(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;

        return new PlayerContext(
                collectGameMode(minecraft, player),
                collectBiome(minecraft, player),
                collectTimeOfDay(minecraft),
                MinecraftResourceNames.dimensionId(minecraft.level),
                collectPosition(player),
                collectInventory(player),
                collectEquipment(player)
        );
    }

    private static String collectGameMode(Minecraft minecraft, LocalPlayer player) {
        if (player == null || minecraft.gameMode == null) {
            return UNKNOWN;
        }
        return minecraft.gameMode.getPlayerMode().name();
    }

    private static String collectBiome(Minecraft minecraft, LocalPlayer player) {
        if (player == null || minecraft.level == null) {
            return UNKNOWN;
        }
        return MinecraftResourceNames.biomeId(
                minecraft.level.getBiome(player.blockPosition())
        );
    }

    private static String collectTimeOfDay(Minecraft minecraft) {
        if (minecraft.level == null) {
            return UNKNOWN;
        }

        long timeInDay = minecraft.level.getOverworldClockTime() % 24000;
        return timeInDay < 12000 ? "DAY" : "NIGHT";
    }

    private static PlayerContext.Position collectPosition(LocalPlayer player) {
        if (player == null) {
            return null;
        }

        BlockPos position = player.blockPosition();
        return new PlayerContext.Position(position.getX(), position.getY(), position.getZ());
    }

    private static Map<String, Integer> collectInventory(LocalPlayer player) {
        Map<String, Integer> inventory = new HashMap<>();
        if (player == null) {
            return inventory;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                inventory.merge(
                        MinecraftResourceNames.itemId(stack.getItem()),
                        stack.getCount(),
                        Integer::sum
                );
            }
        }
        return inventory;
    }

    private static PlayerContext.Equipment collectEquipment(LocalPlayer player) {
        if (player == null) {
            return new PlayerContext.Equipment(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY);
        }

        return new PlayerContext.Equipment(
                itemIdOrEmpty(player.getMainHandItem()),
                itemIdOrEmpty(player.getOffhandItem()),
                itemIdOrEmpty(player.getItemBySlot(EquipmentSlot.HEAD)),
                itemIdOrEmpty(player.getItemBySlot(EquipmentSlot.CHEST)),
                itemIdOrEmpty(player.getItemBySlot(EquipmentSlot.LEGS)),
                itemIdOrEmpty(player.getItemBySlot(EquipmentSlot.FEET))
        );
    }

    private static String itemIdOrEmpty(ItemStack stack) {
        return stack.isEmpty() ? EMPTY : MinecraftResourceNames.itemId(stack.getItem());
    }
}

package com.lucasrocha.craftai.client.presentation;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CraftAiChatPresenter {

    static final int MAX_CHUNK_LENGTH = 700;

    private CraftAiChatPresenter() {}

    public static void answer(FabricClientCommandSource source, String message) {
        send(source::sendFeedback, message, ChatFormatting.WHITE);
    }

    public static void status(FabricClientCommandSource source, String message) {
        send(source::sendFeedback, message, ChatFormatting.GRAY);
    }

    public static void error(FabricClientCommandSource source, String message) {
        send(source::sendFeedback, message, ChatFormatting.RED);
    }

    public static void info(FabricClientCommandSource source, String message) {
        send(source::sendFeedback, message, ChatFormatting.WHITE);
    }

    public static void info(LocalPlayer player, String message) {
        send(player::sendSystemMessage, message, ChatFormatting.WHITE);
    }

    private static void send(
            Consumer<Component> messageSink,
            String message,
            ChatFormatting bodyColor
    ) {
        List<String> chunks = splitMessage(message, MAX_CHUNK_LENGTH);
        messageSink.accept(Component.literal(" "));

        for (String chunk : chunks) {
            MutableComponent line = Component.literal("[CraftAI] ")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
            line.append(Component.literal(chunk).withStyle(bodyColor));
            messageSink.accept(line);
        }
    }

    public static List<String> splitMessage(String message, int maxLength) {
        String remaining = message == null ? "" : message.strip();
        if (remaining.isEmpty()) {
            return List.of("");
        }

        List<String> chunks = new ArrayList<>();
        while (remaining.length() > maxLength) {
            int splitAt = readableBoundary(remaining, maxLength);
            chunks.add(remaining.substring(0, splitAt).stripTrailing());
            remaining = remaining.substring(splitAt).stripLeading();
        }
        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }
        return List.copyOf(chunks);
    }

    private static int readableBoundary(String value, int maxLength) {
        int minimum = maxLength / 2;
        int paragraph = value.lastIndexOf("\n\n", maxLength);
        if (paragraph >= minimum) {
            return paragraph + 2;
        }

        for (String boundary : List.of(". ", "! ", "? ", "\n", " ")) {
            int index = value.lastIndexOf(boundary, maxLength);
            if (index >= minimum) {
                return index + boundary.length();
            }
        }
        return maxLength;
    }
}

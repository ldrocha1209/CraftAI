package com.lucasrocha.craftai.client.data;

import java.util.List;

public record ConversationContext(
        boolean followUp,
        List<Turn> recentTurns,
        ReferencedDestination lastDestination
) {
    public ConversationContext {
        recentTurns = List.copyOf(recentTurns);
        if (!followUp && (!recentTurns.isEmpty() || lastDestination != null)) {
            throw new IllegalArgumentException(
                    "Non-follow-up requests must not transmit conversation history."
            );
        }
    }

    public static ConversationContext none() {
        return new ConversationContext(false, List.of(), null);
    }

    public record Turn(String question, String answer) {}

    public record ReferencedDestination(
            String sourceQuestion,
            WorldQueryResult.Kind kind,
            String target,
            String dimension,
            WorldQueryResult.Position position,
            WorldQueryResult.Navigation navigation,
            long ageSeconds,
            boolean sameDimension
    ) {}
}

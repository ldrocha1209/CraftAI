package com.lucasrocha.craftai.client.intent;

import com.lucasrocha.craftai.client.data.WorldQueryResult;

public record QueryIntent(
        Action action,
        WorldQueryResult.Kind targetType,
        WorldQueryResult.Target targetIdentifier
) {

    public enum Action {
        GENERAL_QUESTION,
        WORLD_SEARCH,
        AMBIGUOUS
    }

    public QueryIntent {
        if (action == null) {
            throw new IllegalArgumentException("An intent action is required.");
        }
        if (targetIdentifier == null && targetType != null) {
            throw new IllegalArgumentException("A target type cannot exist without a target identifier.");
        }
        if (targetIdentifier != null && targetType != targetIdentifier.getKind()) {
            throw new IllegalArgumentException("The target type must match the target identifier.");
        }
        if (action == Action.WORLD_SEARCH && targetIdentifier == null) {
            throw new IllegalArgumentException("A world-search intent requires a target.");
        }
    }

    public static QueryIntent generalQuestion(WorldQueryResult.Target target) {
        return new QueryIntent(
                Action.GENERAL_QUESTION,
                target == null ? null : target.getKind(),
                target
        );
    }

    public static QueryIntent worldSearch(WorldQueryResult.Target target) {
        return new QueryIntent(Action.WORLD_SEARCH, target.getKind(), target);
    }

    public static QueryIntent ambiguous(WorldQueryResult.Target target) {
        return new QueryIntent(
                Action.AMBIGUOUS,
                target == null ? null : target.getKind(),
                target
        );
    }
}

package com.lucasrocha.craftai.client.intent;

import com.lucasrocha.craftai.client.data.WorldQueryResult;
import com.lucasrocha.craftai.client.data.WorldQueryTarget;

public record QueryIntent(
        Action action,
        WorldQueryResult.Kind targetType,
        WorldQueryTarget target
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
        if (target == null && targetType != null) {
            throw new IllegalArgumentException("A target type cannot exist without a target identifier.");
        }
        if (target != null && targetType != target.kind()) {
            throw new IllegalArgumentException("The target type must match the target identifier.");
        }
        if (action == Action.WORLD_SEARCH && target == null) {
            throw new IllegalArgumentException("A world-search intent requires a target.");
        }
    }

    public String targetIdentifier() {
        return target == null ? null : target.identifier();
    }

    public static QueryIntent generalQuestion(WorldQueryTarget target) {
        return new QueryIntent(
                Action.GENERAL_QUESTION,
                target == null ? null : target.kind(),
                target
        );
    }

    public static QueryIntent worldSearch(WorldQueryTarget target) {
        return new QueryIntent(Action.WORLD_SEARCH, target.kind(), target);
    }

    public static QueryIntent ambiguous(WorldQueryTarget target) {
        return new QueryIntent(
                Action.AMBIGUOUS,
                target == null ? null : target.kind(),
                target
        );
    }
}

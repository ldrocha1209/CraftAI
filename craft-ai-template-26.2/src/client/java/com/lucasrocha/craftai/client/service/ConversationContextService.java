package com.lucasrocha.craftai.client.service;

import com.lucasrocha.craftai.client.data.ConversationContext;
import com.lucasrocha.craftai.client.data.PlayerContext;
import com.lucasrocha.craftai.client.data.WorldQueryResult;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

public final class ConversationContextService {

    static final int MAX_TURNS = 3;
    static final long MAX_AGE_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final int MAX_QUESTION_LENGTH = 300;
    private static final int MAX_ANSWER_LENGTH = 1_200;

    private final LongSupplier currentTimeMillis;
    private final Deque<StoredTurn> turns = new ArrayDeque<>();
    private Object sessionIdentity;
    private StoredDestination lastDestination;
    private long lastActivityMillis;

    public ConversationContextService() {
        this(System::currentTimeMillis);
    }

    ConversationContextService(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
    }

    public synchronized ConversationContext snapshot(
            Object currentSessionIdentity,
            boolean followUpLanguage,
            boolean destinationFollowUpLanguage,
            PlayerContext player
    ) {
        ensureSession(currentSessionIdentity);
        expireInactiveContext();

        boolean followUp = followUpLanguage && !turns.isEmpty();
        if (!followUp) {
            return ConversationContext.none();
        }

        List<ConversationContext.Turn> recentTurns = turns.stream()
                .map(turn -> new ConversationContext.Turn(turn.question(), turn.answer()))
                .toList();
        return new ConversationContext(
                true,
                recentTurns,
                destinationFollowUpLanguage
                        ? referencedDestination(player)
                        : null
        );
    }

    public synchronized void recordSuccessfulTurn(
            Object currentSessionIdentity,
            String question,
            String answer,
            WorldQueryResult worldQuery
    ) {
        ensureSession(currentSessionIdentity);
        expireInactiveContext();

        turns.addLast(new StoredTurn(
                truncate(question, MAX_QUESTION_LENGTH),
                truncate(answer, MAX_ANSWER_LENGTH)
        ));
        while (turns.size() > MAX_TURNS) {
            turns.removeFirst();
        }

        if (worldQuery != null) {
            lastDestination = worldQuery.isFound()
                    ? new StoredDestination(
                            truncate(question, MAX_QUESTION_LENGTH),
                            worldQuery.kind(),
                            worldQuery.target(),
                            worldQuery.dimension(),
                            worldQuery.position(),
                            currentTimeMillis.getAsLong()
                    )
                    : null;
        }
        lastActivityMillis = currentTimeMillis.getAsLong();
    }

    private ConversationContext.ReferencedDestination referencedDestination(
            PlayerContext player
    ) {
        if (lastDestination == null) {
            return null;
        }

        long ageMillis = Math.max(
                0,
                currentTimeMillis.getAsLong() - lastDestination.recordedAtMillis()
        );
        if (ageMillis > MAX_AGE_MILLIS) {
            lastDestination = null;
            return null;
        }

        boolean sameDimension = lastDestination.dimension().equals(player.getDimension());
        WorldQueryResult.Navigation navigation = null;
        PlayerContext.Position playerPosition = player.getPosition();
        if (sameDimension && playerPosition != null) {
            navigation = NavigationService.calculate(
                    playerPosition.x(),
                    playerPosition.z(),
                    lastDestination.position().x(),
                    lastDestination.position().z()
            );
        }

        return new ConversationContext.ReferencedDestination(
                lastDestination.sourceQuestion(),
                lastDestination.kind(),
                lastDestination.target(),
                lastDestination.dimension(),
                lastDestination.position(),
                navigation,
                ageMillis / 1_000,
                sameDimension
        );
    }

    private void ensureSession(Object currentSessionIdentity) {
        if (sessionIdentity != currentSessionIdentity) {
            sessionIdentity = currentSessionIdentity;
            turns.clear();
            lastDestination = null;
            lastActivityMillis = 0;
        }
    }

    private void expireInactiveContext() {
        long now = currentTimeMillis.getAsLong();
        if (lastActivityMillis > 0 && now - lastActivityMillis > MAX_AGE_MILLIS) {
            turns.clear();
            lastDestination = null;
            lastActivityMillis = 0;
        }
    }

    private static String truncate(String value, int maxLength) {
        String safeValue = value == null ? "" : value.strip();
        return safeValue.length() <= maxLength
                ? safeValue
                : safeValue.substring(0, maxLength);
    }

    private record StoredTurn(String question, String answer) {}

    private record StoredDestination(
            String sourceQuestion,
            WorldQueryResult.Kind kind,
            String target,
            String dimension,
            WorldQueryResult.Position position,
            long recordedAtMillis
    ) {}
}

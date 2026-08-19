package com.lucasrocha.craftai.client.intent;

import com.lucasrocha.craftai.client.data.AssistanceMode;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AssistanceIntentDetector {

    private static final List<Pattern> GOAL_PATTERNS = List.of(
            Pattern.compile("\\bi want to\\b"),
            Pattern.compile("\\bmy goal is\\b"),
            Pattern.compile(
                    "\\bhelp me (?:to )?(?:find|build|reach|go|prepare|defeat|explore|mine|craft|make)\\b"
            ),
            Pattern.compile("\\bmake (?:me )?a plan\\b"),
            Pattern.compile("\\bplan (?:for|to)\\b"),
            Pattern.compile("\\bwhat (?:do i need|should i bring) to\\b")
    );

    private static final List<Pattern> RECOMMENDATION_PATTERNS = List.of(
            Pattern.compile("\\bam i (?:ready|prepared|equipped)\\b"),
            Pattern.compile("\\bshould i\\b"),
            Pattern.compile("\\bwhat should i do(?: next)?\\b"),
            Pattern.compile("\\bwhat do you recommend\\b"),
            Pattern.compile("\\bdo i have enough .{0,30}\\b(?:for|to)\\b"),
            Pattern.compile("\\bhow prepared am i\\b"),
            Pattern.compile("\\bis my (?:gear|equipment|inventory) (?:good|enough|ready)\\b"),
            Pattern.compile("\\bwhat should i bring\\b")
    );

    private static final List<Pattern> FOLLOW_UP_PATTERNS = List.of(
            Pattern.compile("^(?:and|also|okay so|what about)\\b"),
            Pattern.compile("\\btell me more(?: about (?:that|it))?\\b"),
            Pattern.compile("\\b(?:that|it|there) instead\\b"),
            Pattern.compile(
                    "\\bhow do i (?:make|build|craft|find|get|use) "
                            + "(?:another|a second|one more)(?: one)?\\b"
            ),
            Pattern.compile(
                    "\\b(?:can|could|should) i (?:make|build|craft|get|use) "
                            + "(?:another|a second|one more)(?: one)?\\b"
            ),
            Pattern.compile("\\b(?:another|a second|one more) (?:one|of those|of them)\\b")
    );

    private static final List<Pattern> DESTINATION_FOLLOW_UP_PATTERNS = List.of(
            Pattern.compile("\\bhow far is (?:that|it)\\b"),
            Pattern.compile("\\bwhat direction is (?:that|it)\\b"),
            Pattern.compile("\\bwhere is (?:that|it)\\b"),
            Pattern.compile("\\bhow do i get there\\b"),
            Pattern.compile("\\bwhat should i bring there\\b"),
            Pattern.compile("\\bis (?:that|it) nearby\\b")
    );

    private AssistanceIntentDetector() {}

    public static AssistanceIntent detect(String question) {
        String normalized = normalize(question);
        AssistanceMode mode;
        if (matchesAny(GOAL_PATTERNS, normalized)) {
            mode = AssistanceMode.GOAL_PLAN;
        } else if (matchesAny(RECOMMENDATION_PATTERNS, normalized)) {
            mode = AssistanceMode.RECOMMENDATION;
        } else {
            mode = AssistanceMode.GENERAL;
        }

        boolean destinationFollowUp = matchesAny(
                DESTINATION_FOLLOW_UP_PATTERNS,
                normalized
        );
        return new AssistanceIntent(
                mode,
                destinationFollowUp || matchesAny(FOLLOW_UP_PATTERNS, normalized),
                destinationFollowUp
        );
    }

    private static boolean matchesAny(List<Pattern> patterns, String question) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(question).find());
    }

    private static String normalize(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        return question
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}

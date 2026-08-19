package com.lucasrocha.craftai.client.intent;

import com.lucasrocha.craftai.client.data.WorldQueryResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class QueryIntentDetector {

    private static final List<TargetLanguage> TARGETS = List.of(
            targetLanguage(
                    WorldQueryResult.Target.VILLAGE,
                    "village|villages|villager|villagers"
            ),
            targetLanguage(
                    WorldQueryResult.Target.DESERT,
                    "desert|deserts"
            )
    );

    private static final List<Pattern> AMBIGUOUS_LOCATION_CUES = List.of(
            Pattern.compile("\\bnearby\\b"),
            Pattern.compile("\\bnear me\\b"),
            Pattern.compile("\\baround here\\b"),
            Pattern.compile("\\bin my world\\b")
    );

    private QueryIntentDetector() {}

    public static QueryIntent detect(String question) {
        String normalizedQuestion = normalize(question);
        Set<WorldQueryResult.Target> mentionedTargets = EnumSet.noneOf(WorldQueryResult.Target.class);

        for (TargetLanguage targetLanguage : TARGETS) {
            if (targetLanguage.mentionPattern().matcher(normalizedQuestion).find()) {
                mentionedTargets.add(targetLanguage.target());
            }
        }

        if (mentionedTargets.size() > 1) {
            return QueryIntent.ambiguous(null);
        }

        if (mentionedTargets.isEmpty()) {
            return QueryIntent.generalQuestion(null);
        }

        WorldQueryResult.Target target = mentionedTargets.iterator().next();
        TargetLanguage targetLanguage = languageFor(target);

        if (targetLanguage.requestsLocation(normalizedQuestion)) {
            return QueryIntent.worldSearch(target);
        }

        if (AMBIGUOUS_LOCATION_CUES.stream()
                .anyMatch(pattern -> pattern.matcher(normalizedQuestion).find())) {
            return QueryIntent.ambiguous(target);
        }

        return QueryIntent.generalQuestion(target);
    }

    private static TargetLanguage languageFor(WorldQueryResult.Target target) {
        return TARGETS.stream()
                .filter(targetLanguage -> targetLanguage.target() == target)
                .findFirst()
                .orElseThrow();
    }

    private static TargetLanguage targetLanguage(
            WorldQueryResult.Target target,
            String aliases
    ) {
        String targetPattern = "(?:" + aliases + ")";
        List<Pattern> locationPatterns = new ArrayList<>();

        locationPatterns.add(Pattern.compile(
                "\\b(?:nearest|closest)(?:\\s+\\w+){0,3}\\s+" + targetPattern + "\\b"
        ));
        locationPatterns.add(Pattern.compile(
                "\\bwhere\\s+(?:is|are)(?:\\s+\\w+){0,6}\\s+" + targetPattern + "\\b"
        ));
        locationPatterns.add(Pattern.compile(
                "\\bfind(?:\\s+(?:me|us))?(?:\\s+the)?(?:\\s+(?:nearest|closest))?"
                        + "(?:\\s+(?:a|an))?\\s+" + targetPattern + "\\b"
        ));
        locationPatterns.add(Pattern.compile(
                "\\bget\\s+to(?:\\s+the)?(?:\\s+(?:nearest|closest))?"
                        + "(?:\\s+(?:a|an))?\\s+" + targetPattern + "\\b"
        ));
        locationPatterns.add(Pattern.compile(
                "\\b(?:locate|directions\\s+to)(?:\\s+(?:me|us))?(?:\\s+the)?"
                        + "(?:\\s+(?:nearest|closest))?(?:\\s+(?:a|an))?\\s+"
                        + targetPattern + "\\b"
        ));

        return new TargetLanguage(
                target,
                Pattern.compile("\\b" + targetPattern + "\\b"),
                List.copyOf(locationPatterns)
        );
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

    private record TargetLanguage(
            WorldQueryResult.Target target,
            Pattern mentionPattern,
            List<Pattern> locationPatterns
    ) {
        private boolean requestsLocation(String question) {
            return locationPatterns.stream()
                    .anyMatch(pattern -> pattern.matcher(question).find());
        }
    }
}

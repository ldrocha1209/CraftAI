package com.lucasrocha.craftai.client.intent;

import com.lucasrocha.craftai.client.data.WorldQueryTarget;
import com.lucasrocha.craftai.client.data.WorldQueryTargetCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueryIntentDetector {

    private static final List<TargetLanguage> TARGETS = WorldQueryTargetCatalog.all().stream()
            .map(QueryIntentDetector::targetLanguage)
            .toList();

    private static final List<Pattern> SINGLE_TARGET_LOCATION_CUES = List.of(
            Pattern.compile("\\bnearby\\b"),
            Pattern.compile("\\bnear me\\b"),
            Pattern.compile("\\baround here\\b"),
            Pattern.compile("\\bin my world\\b")
    );

    private QueryIntentDetector() {}

    public static QueryIntent detect(String question) {
        String normalizedQuestion = normalize(question);
        List<TargetMention> mentions = findNonOverlappingMentions(normalizedQuestion);
        Set<WorldQueryTarget> mentionedTargets = new LinkedHashSet<>();
        mentions.forEach(mention -> mentionedTargets.add(mention.target()));

        if (mentionedTargets.size() > 1) {
            return QueryIntent.ambiguous(null);
        }

        if (mentionedTargets.isEmpty()) {
            return QueryIntent.generalQuestion(null);
        }

        WorldQueryTarget target = mentionedTargets.iterator().next();
        TargetLanguage targetLanguage = languageFor(target);

        if (targetLanguage.requestsLocation(normalizedQuestion)
                || SINGLE_TARGET_LOCATION_CUES.stream()
                .anyMatch(pattern -> pattern.matcher(normalizedQuestion).find())) {
            return QueryIntent.worldSearch(target);
        }

        return QueryIntent.generalQuestion(target);
    }

    private static List<TargetMention> findNonOverlappingMentions(String question) {
        List<TargetMention> candidates = new ArrayList<>();

        for (TargetLanguage targetLanguage : TARGETS) {
            Matcher matcher = targetLanguage.mentionPattern().matcher(question);
            while (matcher.find()) {
                candidates.add(new TargetMention(
                        targetLanguage.target(),
                        matcher.start(),
                        matcher.end()
                ));
            }
        }

        candidates.sort(
                Comparator.comparingInt(TargetMention::length)
                        .reversed()
                        .thenComparingInt(TargetMention::start)
        );

        List<TargetMention> selected = new ArrayList<>();
        for (TargetMention candidate : candidates) {
            boolean overlaps = selected.stream().anyMatch(existing -> existing.overlaps(candidate));
            if (!overlaps) {
                selected.add(candidate);
            }
        }

        selected.sort(Comparator.comparingInt(TargetMention::start));
        return selected;
    }

    private static TargetLanguage languageFor(WorldQueryTarget target) {
        return TARGETS.stream()
                .filter(targetLanguage -> targetLanguage.target().equals(target))
                .findFirst()
                .orElseThrow();
    }

    private static TargetLanguage targetLanguage(WorldQueryTarget target) {
        String targetPattern = target.aliases().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|", "(?:", ")"));
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
            WorldQueryTarget target,
            Pattern mentionPattern,
            List<Pattern> locationPatterns
    ) {
        private boolean requestsLocation(String question) {
            return locationPatterns.stream()
                    .anyMatch(pattern -> pattern.matcher(question).find());
        }
    }

    private record TargetMention(WorldQueryTarget target, int start, int end) {
        private int length() {
            return end - start;
        }

        private boolean overlaps(TargetMention other) {
            return start < other.end && other.start < end;
        }
    }
}

package de.venomenon.gridwordsbot.domain.excuse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Selects three diverse, fully renderable templates with style-first weighted randomness. */
public final class ExcuseSelector {

    private static final int OPTION_COUNT = 3;

    private final ExcuseTemplateRenderer renderer;
    private final ExcuseRandom random;

    public ExcuseSelector(ExcuseTemplateRenderer renderer, ExcuseRandom random) {
        this.renderer = Objects.requireNonNull(renderer);
        this.random = Objects.requireNonNull(random);
    }

    public Optional<ExcuseSelection> select(
            ExcuseCatalog catalog,
            ExcuseContext context,
            ExcuseSelectionRequest request) {
        List<Candidate> candidates = catalog.templates().stream()
                .filter(ExcuseTemplate::selectable)
                .filter(template -> template.supports(context))
                .filter(template -> !request.excludedTemplateIds().contains(template.id()))
                .filter(template -> request.requiredStyle().map(style -> style == template.style()).orElse(true))
                .map(template -> renderer.render(template, context).map(text -> new Candidate(template, text)))
                .flatMap(Optional::stream)
                .toList();
        if (candidates.size() < OPTION_COUNT) {
            return Optional.empty();
        }

        List<Candidate> selected = new ArrayList<>(OPTION_COUNT);
        Set<ExcuseStyle> usedStyles = new HashSet<>();
        Set<ExcuseTopic> usedTopics = new HashSet<>(request.discouragedTopics());

        List<Candidate> contextSpecific = candidates.stream()
                .filter(candidate -> candidate.template().isContextSpecific())
                .toList();
        if (!contextSpecific.isEmpty()) {
            int highestSpecificity = contextSpecific.stream()
                    .mapToInt(candidate -> candidate.template().specificity())
                    .max()
                    .orElseThrow();
            List<Candidate> mostSpecific = contextSpecific.stream()
                    .filter(candidate -> candidate.template().specificity() == highestSpecificity)
                    .toList();
            Candidate mandatory = chooseStyleFirst(mostSpecific, Set.of(), usedTopics);
            selected.add(mandatory);
            usedStyles.add(mandatory.template().style());
            usedTopics.add(mandatory.template().topic());
        }

        while (selected.size() < OPTION_COUNT) {
            Set<String> selectedIds = selected.stream()
                    .map(candidate -> candidate.template().id())
                    .collect(java.util.stream.Collectors.toSet());
            List<Candidate> remaining = candidates.stream()
                    .filter(candidate -> !selectedIds.contains(candidate.template().id()))
                    .toList();
            if (remaining.isEmpty()) {
                return Optional.empty();
            }
            Candidate next = chooseStyleFirst(remaining, usedStyles, usedTopics);
            selected.add(next);
            usedStyles.add(next.template().style());
            usedTopics.add(next.template().topic());
        }

        List<ExcuseOption> options = new ArrayList<>(OPTION_COUNT);
        for (int index = 0; index < selected.size(); index++) {
            Candidate candidate = selected.get(index);
            options.add(new ExcuseOption(
                    request.round(),
                    index + 1,
                    candidate.template().id(),
                    candidate.template().style(),
                    candidate.template().topic(),
                    candidate.renderedText()));
        }
        return Optional.of(new ExcuseSelection(request.round(), options));
    }

    private Candidate chooseStyleFirst(
            List<Candidate> candidates,
            Set<ExcuseStyle> usedStyles,
            Set<ExcuseTopic> usedTopics) {
        List<Candidate> styleDiverse = candidates.stream()
                .filter(candidate -> !usedStyles.contains(candidate.template().style()))
                .toList();
        List<Candidate> stylePool = styleDiverse.isEmpty() ? candidates : styleDiverse;

        Map<ExcuseStyle, List<Candidate>> byStyle = new EnumMap<>(ExcuseStyle.class);
        for (Candidate candidate : stylePool) {
            byStyle.computeIfAbsent(candidate.template().style(), ignored -> new ArrayList<>()).add(candidate);
        }
        List<ExcuseStyle> styles = byStyle.keySet().stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        ExcuseStyle selectedStyle = styles.get(random.nextInt(styles.size()));
        List<Candidate> selectedStyleCandidates = byStyle.get(selectedStyle);

        List<Candidate> topicDiverse = selectedStyleCandidates.stream()
                .filter(candidate -> !usedTopics.contains(candidate.template().topic()))
                .toList();
        List<Candidate> topicPool = topicDiverse.isEmpty() ? selectedStyleCandidates : topicDiverse;
        int maximumSpecificity = topicPool.stream()
                .mapToInt(candidate -> candidate.template().specificity())
                .max()
                .orElseThrow();
        List<Candidate> specificityPool = topicPool.stream()
                .filter(candidate -> candidate.template().specificity() == maximumSpecificity)
                .toList();
        return weighted(specificityPool);
    }

    private Candidate weighted(List<Candidate> candidates) {
        long totalWeight = candidates.stream().mapToLong(candidate -> candidate.template().weight()).sum();
        if (totalWeight > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("candidate weights exceed supported range");
        }
        int selected = random.nextInt((int) totalWeight);
        int cumulative = 0;
        for (Candidate candidate : candidates) {
            cumulative += candidate.template().weight();
            if (selected < cumulative) {
                return candidate;
            }
        }
        throw new IllegalStateException("weighted selection did not resolve a candidate");
    }

    private record Candidate(ExcuseTemplate template, String renderedText) {
    }
}

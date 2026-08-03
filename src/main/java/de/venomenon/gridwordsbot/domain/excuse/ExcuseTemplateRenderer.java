package de.venomenon.gridwordsbot.domain.excuse;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves only explicitly known placeholders and rejects incomplete or malformed texts. */
public final class ExcuseTemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-z][A-Za-z0-9]*)}");

    public Analysis analyze(String text) {
        if (text == null) {
            return new Analysis(Set.of(), Set.of(), true);
        }
        Set<ExcusePlaceholder> placeholders = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder remainder = new StringBuilder();
        int previous = 0;
        while (matcher.find()) {
            remainder.append(text, previous, matcher.start());
            ExcusePlaceholder.fromToken(matcher.group(1))
                    .ifPresentOrElse(placeholders::add, () -> unknown.add(matcher.group(1)));
            previous = matcher.end();
        }
        remainder.append(text, previous, text.length());
        boolean malformed = remainder.indexOf("{") >= 0 || remainder.indexOf("}") >= 0;
        return new Analysis(Set.copyOf(placeholders), Set.copyOf(unknown), malformed);
    }

    public Optional<String> render(ExcuseTemplate template, ExcuseContext context) {
        Analysis analysis = analyze(template.text());
        if (!analysis.valid()) {
            return Optional.empty();
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template.text());
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            ExcusePlaceholder placeholder = ExcusePlaceholder.fromToken(matcher.group(1)).orElseThrow();
            Optional<String> value = context.placeholder(placeholder);
            if (value.isEmpty()) {
                return Optional.empty();
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value.get()));
        }
        matcher.appendTail(rendered);
        return Optional.of(rendered.toString());
    }

    public record Analysis(Set<ExcusePlaceholder> placeholders, Set<String> unknownTokens, boolean malformed) {
        public boolean valid() {
            return unknownTokens.isEmpty() && !malformed;
        }
    }
}

package de.venomenon.gridwordsbot.adapter.catalog;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalogCoverage;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalogValidator;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCondition;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTemplate;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTopic;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Strict JSON loader for the versioned editorial catalog. It has no Spring lifecycle or runtime activation. */
public final class JsonExcuseCatalogLoader {

    public static final String SCHEMA_RESOURCE = "excuses/catalog.schema.json";

    private static final Set<String> ROOT_FIELDS = Set.of("version", "templates");
    private static final Set<String> TEMPLATE_FIELDS = Set.of(
            "id", "style", "games", "topic", "specificity", "weight",
            "requiresAll", "excludesAny", "text", "selectable");

    private final JsonMapper mapper;
    private final ExcuseCatalogValidator validator;
    private final ExcuseCatalogCoverage coverage;

    public JsonExcuseCatalogLoader() {
        this(JsonMapper.builder().build(), new ExcuseCatalogValidator(), ExcuseCatalogCoverage.structuralOnly());
    }

    public JsonExcuseCatalogLoader(
            JsonMapper mapper,
            ExcuseCatalogValidator validator,
            ExcuseCatalogCoverage coverage) {
        this.mapper = Objects.requireNonNull(mapper);
        this.validator = Objects.requireNonNull(validator);
        this.coverage = Objects.requireNonNull(coverage);
    }

    public ExcuseCatalog loadResource(ClassLoader classLoader, String resourcePath) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(resourcePath, "resourcePath");
        try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new ExcuseCatalogLoadException(List.of("resource not found: " + resourcePath));
            }
            return load(input);
        } catch (ExcuseCatalogLoadException exception) {
            throw exception;
        } catch (java.io.IOException exception) {
            throw new ExcuseCatalogLoadException("could not close catalog resource " + resourcePath, exception);
        }
    }

    public ExcuseCatalog load(InputStream input) {
        Objects.requireNonNull(input, "input");
        JsonNode root;
        try {
            root = mapper.readTree(input);
        } catch (RuntimeException exception) {
            throw new ExcuseCatalogLoadException("catalog is not valid JSON", exception);
        }
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            throw new ExcuseCatalogLoadException(List.of("catalog root must be a JSON object"));
        }
        rejectUnknownFields(root, ROOT_FIELDS, "catalog", errors);
        String version = requiredText(root, "version", "catalog", errors);
        JsonNode templatesNode = root.get("templates");
        if (templatesNode == null || !templatesNode.isArray()) {
            errors.add("catalog.templates must be an array");
        }

        List<ExcuseTemplate> templates = new ArrayList<>();
        if (templatesNode != null && templatesNode.isArray()) {
            int index = 0;
            for (JsonNode templateNode : templatesNode) {
                parseTemplate(templateNode, index, errors).ifPresent(templates::add);
                index++;
            }
        }
        if (!errors.isEmpty()) {
            throw new ExcuseCatalogLoadException(errors);
        }
        try {
            return validator.validate(new ExcuseCatalog(version, templates), coverage);
        } catch (RuntimeException exception) {
            if (exception instanceof de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalogValidationException validation) {
                throw new ExcuseCatalogLoadException(validation.errors());
            }
            throw new ExcuseCatalogLoadException("catalog structure is invalid", exception);
        }
    }

    private java.util.Optional<ExcuseTemplate> parseTemplate(
            JsonNode node, int index, List<String> errors) {
        String location = "catalog.templates[" + index + "]";
        if (node == null || !node.isObject()) {
            errors.add(location + " must be an object");
            return java.util.Optional.empty();
        }
        rejectUnknownFields(node, TEMPLATE_FIELDS, location, errors);
        int errorCountBefore = errors.size();
        String id = requiredText(node, "id", location, errors);
        ExcuseStyle style = parseEnum(node, "style", ExcuseStyle.class, location, errors);
        Set<GameType> games = parseEnumSet(node, "games", GameType.class, location, errors);
        ExcuseTopic topic = parseEnum(node, "topic", ExcuseTopic.class, location, errors);
        int specificity = requiredInt(node, "specificity", location, errors);
        int weight = requiredInt(node, "weight", location, errors);
        Set<ExcuseCondition> requiresAll = parseConditions(node, "requiresAll", location, errors);
        Set<ExcuseCondition> excludesAny = parseConditions(node, "excludesAny", location, errors);
        String text = requiredText(node, "text", location, errors);
        boolean selectable = requiredBoolean(node, "selectable", location, errors);
        if (errors.size() != errorCountBefore) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new ExcuseTemplate(
                    id, style, games, topic, specificity, weight, requiresAll, excludesAny, text, selectable));
        } catch (RuntimeException exception) {
            errors.add(location + " is invalid: " + exception.getMessage());
            return java.util.Optional.empty();
        }
    }

    private static void rejectUnknownFields(
            JsonNode node, Set<String> allowed, String location, List<String> errors) {
        for (String field : node.propertyNames()) {
            if (!allowed.contains(field)) {
                errors.add(location + " contains unknown field " + field);
            }
        }
    }

    private static String requiredText(
            JsonNode node, String field, String location, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            errors.add(location + "." + field + " must be a non-blank string");
            return "invalid";
        }
        return value.asString();
    }

    private static int requiredInt(
            JsonNode node, String field, String location, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            errors.add(location + "." + field + " must be an integer");
            return 0;
        }
        return value.intValue();
    }

    private static boolean requiredBoolean(
            JsonNode node, String field, String location, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            errors.add(location + "." + field + " must be a boolean");
            return false;
        }
        return value.booleanValue();
    }

    private static <E extends Enum<E>> E parseEnum(
            JsonNode node,
            String field,
            Class<E> type,
            String location,
            List<String> errors) {
        String raw = requiredText(node, field, location, errors);
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            errors.add(location + "." + field + " contains unknown value " + raw);
            return type.getEnumConstants()[0];
        }
    }

    private static <E extends Enum<E>> Set<E> parseEnumSet(
            JsonNode node,
            String field,
            Class<E> type,
            String location,
            List<String> errors) {
        JsonNode values = node.get(field);
        if (values == null || !values.isArray() || values.size() == 0) {
            errors.add(location + "." + field + " must be a non-empty array");
            return Set.of();
        }
        Set<E> parsed = new LinkedHashSet<>();
        int index = 0;
        for (JsonNode value : values) {
            if (!value.isString() || value.asString().isBlank()) {
                errors.add(location + "." + field + "[" + index + "] must be a non-blank string");
            } else {
                String raw = value.asString();
                try {
                    E converted = Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
                    if (!parsed.add(converted)) {
                        errors.add(location + "." + field + " contains duplicate value " + raw);
                    }
                } catch (IllegalArgumentException exception) {
                    errors.add(location + "." + field + " contains unknown value " + raw);
                }
            }
            index++;
        }
        return Set.copyOf(parsed);
    }

    private static Set<ExcuseCondition> parseConditions(
            JsonNode node, String field, String location, List<String> errors) {
        JsonNode values = node.get(field);
        if (values == null || !values.isArray()) {
            errors.add(location + "." + field + " must be an array");
            return Set.of();
        }
        Set<ExcuseCondition> parsed = new LinkedHashSet<>();
        int index = 0;
        for (JsonNode value : values) {
            if (!value.isString() || value.asString().isBlank()) {
                errors.add(location + "." + field + "[" + index + "] must be a non-blank string");
            } else {
                String raw = value.asString();
                ExcuseCondition.fromKey(raw).ifPresentOrElse(condition -> {
                    if (!parsed.add(condition)) {
                        errors.add(location + "." + field + " contains duplicate value " + raw);
                    }
                }, () -> errors.add(location + "." + field + " contains unknown value " + raw));
            }
            index++;
        }
        return Set.copyOf(parsed);
    }
}

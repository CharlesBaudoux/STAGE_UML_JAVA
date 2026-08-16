package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class RulesReader {
    private static final String TYPES = "types";
    private static final String HIDE = "hide";
    private static final String OMIT = "omit";
    private static final String STRATEGY = "strategy";
    private static final String VIEWS = "views";
    private static final String VIEWS_BY_PACKAGE = "viewsByPackage";
    private static final String TITLE = "title";
    private static final String SHOW_EMPTY_MEMBERS = "showEmptyMembers";
    private static final String ORDER = "order";
    private static final String MULTIPLICITY = "multiplicity";
    private static final String DIRECTION = "direction";

    private static final String SCHEMA = "$schema";

    private static final char BOM = (char) 0xFEFF;

    private static final String NAME = "name";
    private static final String RETAIN = "retain";
    private static final String REMOVE = "remove";
    private static final String PACKAGE = "package";
    private static final String AROUND = "around";

    private enum Omission {
        VISIBILITY("visibility"),
        PARAMETERS_NAMES("parameter names"),
        RETURN_VOID("return void"),
        ABSTRACT("abstract");

        private final String written;

        Omission(String written) {
            this.written = written;
        }
    }

    private static final ObjectMapper Parser = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    private RulesReader() {}

    private record Place(String origin, String path) {
        Place in(String key) {
            return new Place(origin, path.isEmpty() ? key : path + "." + key);
        }

        Place index(int rank) {
            return new Place(origin, path + "[" + rank + "]");
        }

        @Override
        public String toString() {
            return path.isEmpty() ? origin : origin + ", " + path;
        }
    }

    static Rules read(String content, String origin) {
        Place root = new Place(origin, "");
        JsonNode object = tree(content, root);

        Rules.Constructor rules = Rules.constructor();

        for (Map.Entry<String, JsonNode> field : object.properties()) {
            String key = field.getKey();
            JsonNode value = field.getValue();
            Place placed = root.in(key);

            if (isANote(key) || key.equals(SCHEMA)) {
                continue;
            }

            switch (key) {
                case TYPES -> rules.types(readTypes(value, placed));
                case HIDE -> rules.hiddenPatterns(readHide(value, placed));
                case OMIT -> omissions(rules, readOmit(value, placed));
                case STRATEGY -> rules.strategy(readStrategy(value, placed));
                case VIEWS -> rules.views(readViews(value, placed));
                case VIEWS_BY_PACKAGE -> rules.viewsByPackage(readBoolean(value, placed));
                case TITLE -> rules.title(readTitle(value, placed));
                case SHOW_EMPTY_MEMBERS -> rules.showEmptyMembers(readBoolean(value, placed));
                case ORDER -> rules.orderDeclaration(readOrder(value, placed));
                case MULTIPLICITY -> rules.inferredMultiplicities(readMultiplicity(value, placed));
                case DIRECTION -> rules.direction(readStream(value, placed));
                default -> throw new IllegalArgumentException(root + ": unknown rule \"" + key
                    + "\". Known rules: " + STRATEGY + ", " + TYPES + ", " + HIDE + ", " + OMIT
                    + ", " + TITLE + ", " + DIRECTION + ", " + ORDER + ", " + MULTIPLICITY + ", "
                    + SHOW_EMPTY_MEMBERS + ", " + VIEWS + ", " + VIEWS_BY_PACKAGE + ". A key whose"
                    + " name starts with \"_\" is ignored, and that is where a note goes; \""
                    + SCHEMA + "\" is ignored too, and that is where the schema goes.");
            }
        }
        return rules.build();
    }

    private static boolean isANote(String key) {
        return key.startsWith("_");
    }

    private static void omissions(Rules.Constructor rules, Set<Omission> requested) {
        rules.omitVisibility(requested.contains(Omission.VISIBILITY))
            .parametersOmitNames(requested.contains(Omission.PARAMETERS_NAMES))
            .omitReturnVoid(requested.contains(Omission.RETURN_VOID))
            .abstractOmit(requested.contains(Omission.ABSTRACT));
    }

    private static JsonNode tree(String content, Place root) {
        if (content.isBlank()) {
            return Parser.createObjectNode();
        }

        checkArgument(content.charAt(0) != BOM,
            "%s: the file starts with a UTF-8 byte order mark, which JSON does not allow. The mark "
                + "is invisible in an editor; re-save the file as UTF-8 without BOM.", root);

        JsonNode lu;
        try {
            lu = Parser.readTree(content);
        } catch (JsonProcessingException e) {
            JsonLocation or = e.getLocation();
            String line = or == null ? "" : ", line " + or.getLineNr();
            throw new IllegalArgumentException(root + line + ": this is not valid JSON — "
                + e.getOriginalMessage() + ". Note that JSON has no comments: a key whose name "
                + "starts with \"_\" is ignored, and that is where a note goes.", e);
        }
        checkArgument(lu.isObject(),
            "%s: expected a JSON object, for example { \"strategy\": \"public\" }; got %s.",
            root, kind(lu));

        return lu;
    }

    private static boolean readOrder(JsonNode value, Place placed) {
        String written = text(value, placed);
        return switch (written) {
            case "declaration" -> true;
            case "alphabetical" -> false;
            default -> throw new IllegalArgumentException(placed + ": expected \"declaration\" or "
                + "\"alphabetical\", got \"" + written + "\".");
        };
    }

    private static boolean readMultiplicity(JsonNode value, Place placed) {
        String written = text(value, placed);
        checkArgument("brackets".equals(written),
            "%s: expected \"brackets\", got \"%s\".", placed, written);

        return true;
    }

    private static String readTitle(JsonNode value, Place placed) {
        String written = text(value, placed);
        checkArgument(!written.isBlank(),
            "%s: the title cannot be empty or made of whitespace only. To have no title, leave "
                + "the key out.", placed);

        return written;
    }

    private static ImmutableList<ViewDefinition> readViews(JsonNode value, Place placed) {
        checkArgument(value.isArray(),
            "%s: expected an array of views, for example [ { \"name\": \"Mapping\", "
                + "\"retain\": [\"KeysymEntry\"] } ]; got %s.",
            placed, kind(value));

        Map<String, ViewDefinition> views = new LinkedHashMap<>();

        for (int rank = 0; rank < value.size(); rank++) {
            Place or = placed.index(rank);
            ViewDefinition view = readView(value.get(rank), or);
            checkArgument(!views.containsKey(view.name()),
                "%s: view \"%s\" is already defined. Keep only one of the two.", or, view.name());

            views.put(view.name(), view);
        }
        return ImmutableList.copyOf(views.values());
    }

    private static ViewDefinition readView(JsonNode value, Place placed) {
        checkArgument(value.isObject(),
            "%s: expected a view object, with a \"%s\" and a \"%s\"; got %s.",
            placed, NAME, RETAIN, kind(value));

        String name = null;
        ImmutableList<Selector> kept = null;
        ImmutableList<Selector> removed = ImmutableList.of();
        UMLFlow direction = UMLFlow.TOP_TO_BOTTOM;

        for (Map.Entry<String, JsonNode> field : value.properties()) {
            String key = field.getKey();
            Place or = placed.in(key);

            if (isANote(key)) {
                continue;
            }

            switch (key) {
                case NAME -> name = viewReadName(field.getValue(), or);
                case RETAIN -> kept = selectors(field.getValue(), or);
                case REMOVE -> removed = selectors(field.getValue(), or);
                case DIRECTION -> direction = readStream(field.getValue(), or);
                default -> throw new IllegalArgumentException(placed + ": unknown key \"" + key
                    + "\" in a view. Known keys: " + NAME + ", " + RETAIN + ", " + REMOVE + ", "
                    + DIRECTION + ".");
            }
        }
        checkArgument(name != null, "%s: this view has no \"%s\".", placed, NAME);
        checkArgument(kept != null,
            "%s: view \"%s\" has no \"%s\"; write at least one class or package.",
            placed, name, RETAIN);
        checkArgument(!kept.isEmpty(),
            "%s: view \"%s\" retains nothing; write at least one class or package.", placed, name);

        return new ViewDefinition(name, kept, removed, direction);
    }

    private static String viewReadName(JsonNode value, Place placed) {
        String name = text(value, placed);
        checkArgument(!name.isBlank(),
            "%s: the view name cannot be empty or made of whitespace only.", placed);

        return name;
    }

    private static ImmutableList<Selector> selectors(JsonNode value, Place placed) {
        checkArgument(value.isArray(),
            "%s: expected an array of selectors, for example [\"Invoice\", { \"%s\": "
                + "\"io.example.model\" }]; got %s.",
            placed, PACKAGE, kind(value));

        ImmutableList.Builder<Selector> read = ImmutableList.builder();
        for (int rank = 0; rank < value.size(); rank++) {
            read.add(selector(value.get(rank), placed.index(rank)));
        }
        return read.build();
    }

    private static Selector selector(JsonNode value, Place placed) {
        if (value.isTextual()) {
            return Selector.clazz(nameDesignates(value, placed, "class"));
        }
        checkArgument(value.isObject(),
            "%s: expected a class name, or { \"%s\": … }, or { \"%s\": … }; got %s.",
            placed, PACKAGE, AROUND, kind(value));

        checkArgument(value.size() == 1,
            "%s: a selector object carries exactly one key, \"%s\" or \"%s\"; this one carries %s."
                + " Write one selector per entry of the array.",
            placed, PACKAGE, AROUND, value.size());

        Map.Entry<String, JsonNode> only = value.properties().iterator().next();
        Place or = placed.in(only.getKey());
        return switch (only.getKey()) {
            case PACKAGE -> Selector.packageOf(nameDesignates(only.getValue(), or, PACKAGE));
            case AROUND -> Selector.neighborhood(nameDesignates(only.getValue(), or, AROUND));
            default -> throw new IllegalArgumentException(placed + ": unknown selector \""
                + only.getKey() + "\". A string names a class; \"" + PACKAGE + "\" takes a whole "
                + "package, and \"" + AROUND + "\" a class and its immediate neighbours.");
        };
    }

    private static String nameDesignates(JsonNode value, Place placed, String kind) {
        String name = text(value, placed);
        checkArgument(!name.isBlank(),
            "%s: a %s selector cannot be empty or made of whitespace only.", placed, kind);

        checkArgument(!containsASpace(name),
            "%s: \"%s\" contains a space, so it is not a name. Write one selector per entry of "
                + "the array.", placed, name);

        return name;
    }

    private static UMLFlow readStream(JsonNode value, Place placed) {
        String written = text(value, placed);
        return switch (written) {
            case "top-to-bottom" -> UMLFlow.TOP_TO_BOTTOM;
            case "left-to-right" -> UMLFlow.LEFT_TO_RIGHT;
            default -> throw new IllegalArgumentException(placed + ": expected \"top-to-bottom\" or "
                + "\"left-to-right\", got \"" + written + "\".");
        };
    }

    private static UMLVisibility readStrategy(JsonNode value, Place placed) {
        String written = text(value, placed);

        UMLVisibility read = null;
        for (UMLVisibility candidate : UMLVisibility.values()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(written)) {
                read = candidate;
            }
        }
        checkArgument(read != null, "%s: unknown strategy \"%s\". Known strategies: none, public, "
            + "protected, package, private — in lower case, like every other word of the file.",
            placed, written);

        checkArgument(read != UMLVisibility.PROJECT,
            "%s: \"project\" is what a class writes to defer to this key; the project itself "
                + "must name a real strategy.", placed);

        return read;
    }

    private static ImmutableList<NamePattern> readHide(JsonNode value, Place placed) {
        checkArgument(value.isArray(),
            "%s: expected an array of name patterns, for example [\"get*\", \"log\"]; got %s.",
            placed, kind(value));

        ImmutableList.Builder<NamePattern> patterns = ImmutableList.builder();

        Set<String> already = new LinkedHashSet<>();

        for (int rank = 0; rank < value.size(); rank++) {
            Place or = placed.index(rank);
            String pattern = text(value.get(rank), or);

            checkArgument(!pattern.isBlank(),
                "%s: a name pattern cannot be empty or made of whitespace only.", or);
            checkArgument(!containsASpace(pattern),
                "%s: a name pattern cannot contain a space (read \"%s\"). Write one pattern per "
                    + "entry of the array, or did you mean \"" + OMIT + "\"?",
                or, pattern);

            checkArgument(!pattern.contains("."),
                "%s: \"%s\" names a member of one class, and a rule never designates an element "
                    + "by name. Put @UMLExclude on that member instead, or write a pattern that "
                    + "describes a family of names.",
                or, pattern);

            checkArgument(!pattern.chars().allMatch(character -> character == '*'),
                "%s: the pattern \"%s\" would hide every member of every class.", or, pattern);
            checkArgument(!already.contains(pattern), "%s: \"%s\" is already asked for.", or, pattern);

            already.add(pattern);
            patterns.add(NamePattern.of(pattern));
        }
        return patterns.build();
    }

    private static Set<Omission> readOmit(JsonNode value, Place placed) {
        checkArgument(value.isArray(),
            "%s: expected an array, for example [\"%s\", \"%s\"]; got %s.",
            placed, Omission.VISIBILITY.written, Omission.RETURN_VOID.written, kind(value));

        Set<Omission> omissions = EnumSet.noneOf(Omission.class);
        for (int rank = 0; rank < value.size(); rank++) {
            Place or = placed.index(rank);
            String written = text(value.get(rank), or);

            Omission requested = null;
            for (Omission candidate : Omission.values()) {
                if (candidate.written.equals(written)) {
                    requested = candidate;
                }
            }
            checkArgument(requested != null, "%s: expected \"%s\", \"%s\", \"%s\" or \"%s\"; got "
                + "\"%s\".", or, Omission.VISIBILITY.written, Omission.PARAMETERS_NAMES.written,
                Omission.RETURN_VOID.written, Omission.ABSTRACT.written, written);

            checkArgument(!omissions.contains(requested),
                "%s: \"%s\" is already asked for.", or, written);

            omissions.add(requested);
        }
        return omissions;
    }

    private static ImmutableMap<String, String> readTypes(JsonNode value, Place placed) {
        checkArgument(value.isObject(),
            "%s: expected an object, for example { \"ImmutableList\": \"List\" }; got %s.",
            placed, kind(value));

        ImmutableMap.Builder<String, String> alias = ImmutableMap.builder();
        for (Map.Entry<String, JsonNode> field : value.properties()) {
            String source = field.getKey();

            if (isANote(source)) {
                continue;
            }
            Place or = placed.in(source);
            String displayed = text(field.getValue(), or);

            checkArgument(!source.isBlank(),
                "%s: the type to replace cannot be empty or made of whitespace only.", or);
            checkArgument(!displayed.isBlank(),
                "%s: the displayed type cannot be empty. To hide a type, there is nothing to "
                    + "write: leave the entry out.", or);

            checkArgument(!containsASpace(source) && !containsASpace(displayed),
                "%s: a type name cannot contain a space (read \"%s\" -> \"%s\").",
                or, source, displayed);

            alias.put(source, displayed);
        }
        return alias.build();
    }

    private static boolean containsASpace(String text) {
        return text.chars()
            .anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }

    private static boolean readBoolean(JsonNode value, Place placed) {
        checkArgument(value.isBoolean(),
            "%s: expected true or false, got %s.", placed, kind(value));

        return value.booleanValue();
    }

    private static String text(JsonNode value, Place placed) {
        checkArgument(value.isTextual(), "%s: expected a string, got %s.", placed, kind(value));

        return value.textValue();
    }

    private static String kind(JsonNode value) {
        return switch (value.getNodeType()) {
            case ARRAY -> "an array";
            case BOOLEAN -> "a boolean";
            case NULL -> "null";
            case NUMBER -> "a number";
            case OBJECT -> "an object";
            case STRING -> "a string";

            default -> "\"" + value + "\"";
        };
    }
}

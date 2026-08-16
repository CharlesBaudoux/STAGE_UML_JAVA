package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Rules {
    public static final String FILE_NAME = "umlg.json";

    private static final Rules No = constructor().build();

    private final ImmutableMap<String, String> alias;

    private final boolean omitReturnVoid;

    private final UMLVisibility strategy;

    private final boolean strategyDeclared;

    private final ImmutableList<NamePattern> hiddenMembers;

    private final ImmutableList<ViewDefinition> views;

    private final boolean viewsByPackage;

    private final String title;

    private final UMLFlow direction;

    private final boolean hideEmptyMembers;

    private final boolean omitVisibility;

    private final boolean parametersOmitNames;

    private final boolean orderDeclaration;

    private final boolean inferredMultiplicities;

    private final boolean abstractOmit;

    private final Set<String> aliasUsed = ConcurrentHashMap.newKeySet();

    private final boolean atLeastOneRule;

    private Rules(Constructor placed) {
        this.alias = placed.alias;
        this.omitReturnVoid = placed.omitReturnVoid;
        this.strategy = placed.strategy;
        this.strategyDeclared = placed.strategyDeclared;
        this.hiddenMembers = placed.hiddenMembers;
        this.views = placed.views;
        this.viewsByPackage = placed.viewsByPackage;
        this.title = placed.title;
        this.direction = placed.direction;
        this.hideEmptyMembers = placed.hideEmptyMembers;
        this.omitVisibility = placed.omitVisibility;
        this.parametersOmitNames = placed.parametersOmitNames;
        this.orderDeclaration = placed.orderDeclaration;
        this.inferredMultiplicities = placed.inferredMultiplicities;
        this.abstractOmit = placed.abstractOmit;
        this.atLeastOneRule = placed.atLeastOneRule;
    }

    static Constructor constructor() {
        return new Constructor();
    }

    static final class Constructor {
        private ImmutableMap<String, String> alias = ImmutableMap.of();
        private boolean omitReturnVoid;
        private UMLVisibility strategy = UMLVisibility.NONE;
        private boolean strategyDeclared;
        private ImmutableList<NamePattern> hiddenMembers = ImmutableList.of();
        private ImmutableList<ViewDefinition> views = ImmutableList.of();
        private boolean viewsByPackage;
        private String title = "";
        private UMLFlow direction = UMLFlow.TOP_TO_BOTTOM;
        private boolean hideEmptyMembers = true;
        private boolean omitVisibility;
        private boolean parametersOmitNames;
        private boolean orderDeclaration;
        private boolean inferredMultiplicities;
        private boolean abstractOmit;
        private boolean atLeastOneRule;

        private Constructor() {}

        private Constructor written() {
            atLeastOneRule = true;
            return this;
        }

        Constructor types(ImmutableMap<String, String> alias) {
            this.alias = checkNotNull(alias);
            return written();
        }

        Constructor hiddenPatterns(ImmutableList<NamePattern> hiddenMembers) {
            this.hiddenMembers = checkNotNull(hiddenMembers);
            return written();
        }

        Constructor strategy(UMLVisibility strategy) {
            this.strategy = checkNotNull(strategy);
            this.strategyDeclared = true;
            return written();
        }

        Constructor views(ImmutableList<ViewDefinition> views) {
            this.views = checkNotNull(views);
            return written();
        }

        Constructor viewsByPackage(boolean viewsByPackage) {
            this.viewsByPackage = viewsByPackage;
            return written();
        }

        Constructor title(String title) {
            this.title = checkNotNull(title);
            return written();
        }

        Constructor direction(UMLFlow direction) {
            this.direction = checkNotNull(direction);
            return written();
        }

        Constructor showEmptyMembers(boolean show) {
            this.hideEmptyMembers = !show;
            return written();
        }

        Constructor omitVisibility(boolean omitVisibility) {
            this.omitVisibility = omitVisibility;
            return written();
        }

        Constructor parametersOmitNames(boolean parametersOmitNames) {
            this.parametersOmitNames = parametersOmitNames;
            return written();
        }

        Constructor omitReturnVoid(boolean omitReturnVoid) {
            this.omitReturnVoid = omitReturnVoid;
            return written();
        }

        Constructor abstractOmit(boolean abstractOmit) {
            this.abstractOmit = abstractOmit;
            return written();
        }

        Constructor orderDeclaration(boolean orderDeclaration) {
            this.orderDeclaration = orderDeclaration;
            return written();
        }

        Constructor inferredMultiplicities(boolean inferredMultiplicities) {
            this.inferredMultiplicities = inferredMultiplicities;
            return written();
        }

        Rules build() {
            return new Rules(this);
        }
    }

    public boolean omitVisibility() {
        return omitVisibility;
    }

    public String title() {
        return title;
    }

    public UMLFlow direction() {
        return direction;
    }

    public boolean hideEmptyMembers() {
        return hideEmptyMembers;
    }

    public ImmutableList<ViewDefinition> views() {
        return views;
    }

    public boolean viewsByPackage() {
        return viewsByPackage;
    }

    public UMLVisibility strategy() {
        return strategy;
    }

    public boolean strategyDeclared() {
        return strategyDeclared;
    }

    public boolean parametersOmitNames() {
        return parametersOmitNames;
    }

    public boolean orderDeclaration() {
        return orderDeclaration;
    }

    public boolean inferredMultiplicities() {
        return inferredMultiplicities;
    }

    public boolean abstractOmit() {
        return abstractOmit;
    }

    public UMLVisibility strategy(UMLVisibility declared) {
        checkNotNull(declared);
        return declared == UMLVisibility.PROJECT ? strategy : declared;
    }

    public boolean hiddenMember(String name) {
        checkNotNull(name);
        for (NamePattern pattern : hiddenMembers) {
            if (pattern.matches(name)) {
                return true;
            }
        }
        return false;
    }

    public ImmutableList<String> rulesWithoutEffect() {
        ImmutableList.Builder<String> dead = ImmutableList.builder();
        for (var rule : alias.entrySet()) {
            if (!aliasUsed.contains(rule.getKey())) {
                dead.add("types: " + rule.getKey() + " -> " + rule.getValue());
            }
        }
        for (NamePattern pattern : hiddenMembers) {
            if (pattern.withoutEffect()) {
                dead.add("hide: " + pattern.written());
            }
        }
        return dead.build();
    }

    public static Rules no() {
        return No;
    }

    public static Rules project(Path root) throws IOException {
        checkNotNull(root);
        Path file = root.resolve(FILE_NAME);

        if (!Files.isRegularFile(file)) {
            return no();
        }
        return RulesReader.read(utf8Content(file), file.toString());
    }

    public static Rules file(Path file) throws IOException {
        checkNotNull(file);
        return RulesReader.read(utf8Content(file), file.toString());
    }

    private static String utf8Content(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(file + " is not encoded in UTF-8, and the "
                + "rules file must be. Re-save it as UTF-8 — an accented letter in a title is "
                + "enough to reveal another encoding.", e);
        }
    }

    public static Rules read(String content, String origin) {
        return RulesReader.read(content, origin);
    }

    public String nameDisplayed(Class<?> type) {
        checkNotNull(type);

        String complete = alias.get(type.getName());
        if (complete != null) {
            aliasUsed.add(type.getName());
            return complete;
        }
        String simple = alias.get(type.getSimpleName());
        if (simple != null) {
            aliasUsed.add(type.getSimpleName());
            return simple;
        }
        return type.getSimpleName();
    }

    public String nameDisplayedReturn(Class<?> type) {
        checkNotNull(type);

        if (omitReturnVoid && type == void.class) {
            return "";
        }
        return nameDisplayed(type);
    }

    public boolean empty() {
        return !atLeastOneRule;
    }
}

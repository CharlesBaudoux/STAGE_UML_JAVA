package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VisibilityPolicyTest {

    @UMLClass
    static class MissingStrategy {
        public String publicField;
        public void methodPublicBusiness() {}
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class PublicStrategy {
        public String publicField;
        protected String protectedField;
        String fieldPackage;
        private String privateField;

        public void publicMethod() {}
        protected void protectedMethod() {}
        void methodPackage() {}
        private void privateMethod() {}
    }

    @UMLClass(strategy = UMLVisibility.PROTECTED)
    static class ProtectedStrategy {
        public String publicField;
        protected String protectedField;
        String fieldPackage;
        private String privateField;
    }

    @UMLClass(strategy = UMLVisibility.PACKAGE)
    static class StrategyPackage {
        public String publicField;
        protected String protectedField;
        String fieldPackage;
        private String privateField;
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class PrivateStrategy {
        public String publicField;
        protected String protectedField;
        String fieldPackage;
        private String privateField;
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class ExemptionInclude {
        @UMLInclude
        private String secretCritical;

        private String secretIgnorable;
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class ExemptionExclude {
        @UMLExclude
        private String fieldTechnical;

        private String fieldBusiness;

        @UMLExclude
        public void methodHiddenBusiness() {}

        public void methodBusinessVisible() {}
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class ExcludeWinsOnInclude {
        @UMLInclude
        @UMLExclude
        private String fieldAmbiguous;
    }

    private static Optional<UmlField> field(UmlEntity entity, String name) {
        return entity.fields().stream().filter(f -> f.name().equals(name)).findFirst();
    }

    private static Optional<UmlMethod> method(UmlEntity entity, String name) {
        return entity.methods().stream().filter(m -> m.name().equals(name)).findFirst();
    }

    @Test
    void noStrategy_producesAnEmptyShell() {
        UmlEntity entity = EntityExtractor.extract(MissingStrategy.class);

        assertTrue(entity.fields().isEmpty(),
            "Without a strategy, no field must be extracted.");
        assertTrue(entity.methods().isEmpty(),
            "Without a strategy, no method must be extracted.");
    }

    @Test
    void publicStrategy_keepsPublicMembersOnly() {
        UmlEntity entity = EntityExtractor.extract(PublicStrategy.class);

        assertTrue(field(entity, "publicField").isPresent());
        assertTrue(field(entity, "protectedField").isEmpty());
        assertTrue(field(entity, "fieldPackage").isEmpty());
        assertTrue(field(entity, "privateField").isEmpty());

        assertTrue(method(entity, "publicMethod").isPresent());
        assertTrue(method(entity, "protectedMethod").isEmpty());
        assertTrue(method(entity, "methodPackage").isEmpty());
        assertTrue(method(entity, "privateMethod").isEmpty());
    }

    @Test
    void protectedStrategy_keepsPublicAndProtected() {
        UmlEntity entity = EntityExtractor.extract(ProtectedStrategy.class);

        assertTrue(field(entity, "publicField").isPresent());
        assertTrue(field(entity, "protectedField").isPresent());
        assertTrue(field(entity, "fieldPackage").isEmpty(),
            "PROTECTED must not include package-private members.");
        assertTrue(field(entity, "privateField").isEmpty());
    }

    @Test
    void packageStrategy_keepsPublicAndPackagePrivateButNotProtected() {
        UmlEntity entity = EntityExtractor.extract(StrategyPackage.class);

        assertTrue(field(entity, "publicField").isPresent());
        assertTrue(field(entity, "fieldPackage").isPresent());
        assertTrue(field(entity, "protectedField").isEmpty(),
            "Per the spec, PACKAGE explicitly excludes protected members.");
        assertTrue(field(entity, "privateField").isEmpty());
    }

    @Test
    void privateStrategy_keepsEveryField() {
        UmlEntity entity = EntityExtractor.extract(PrivateStrategy.class);

        assertTrue(field(entity, "publicField").isPresent());
        assertTrue(field(entity, "protectedField").isPresent());
        assertTrue(field(entity, "fieldPackage").isPresent());
        assertTrue(field(entity, "privateField").isPresent());
    }

    @Test
    void umlInclude_forcesAPrivateFieldDespiteThePublicStrategy() {
        UmlEntity entity = EntityExtractor.extract(ExemptionInclude.class);

        assertTrue(field(entity, "secretCritical").isPresent(),
            "@UMLInclude must force the display even if the field is private.");
        assertTrue(field(entity, "secretIgnorable").isEmpty(),
            "Without @UMLInclude, a private field must stay hidden under strategy PUBLIC.");
    }

    @Test
    void umlExclude_hidesAFieldAllowedByTheStrategy() {
        UmlEntity entity = EntityExtractor.extract(ExemptionExclude.class);

        assertTrue(field(entity, "fieldTechnical").isEmpty(),
            "@UMLExclude must hide the field even if strategy PRIVATE would allow it.");
        assertTrue(field(entity, "fieldBusiness").isPresent());
    }

    @Test
    void umlExclude_hidesABusinessMethodAllowedByTheStrategy() {
        UmlEntity entity = EntityExtractor.extract(ExemptionExclude.class);

        assertTrue(method(entity, "methodHiddenBusiness").isEmpty());
        assertTrue(method(entity, "methodBusinessVisible").isPresent());
    }

    @Test
    void umlExclude_alwaysWinsOverUmlInclude() {
        UmlEntity entity = EntityExtractor.extract(ExcludeWinsOnInclude.class);

        assertTrue(field(entity, "fieldAmbiguous").isEmpty(),
            "On conflict, @UMLExclude must win over @UMLInclude.");
    }
}

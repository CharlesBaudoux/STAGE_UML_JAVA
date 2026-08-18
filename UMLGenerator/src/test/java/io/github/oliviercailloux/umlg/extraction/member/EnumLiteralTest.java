package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import org.junit.jupiter.api.Test;

class EnumLiteralTest {

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    enum Currency {
        Euro, Dollar
    }

    @Test
    void literals_areExtractedIntoTheirOwnList() {
        assertEquals(ImmutableList.of("Euro", "Dollar"),
            EntityExtractor.extract(Currency.class).literals());
    }

    @Test
    void literals_areNoLongerFields() {
        assertTrue(EntityExtractor.extract(Currency.class).fields().isEmpty());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    enum Planet {
        Earth(1.0), MARCH(0.5);

        private final double mass;

        Planet(double mass) {
            this.mass = mass;
        }

        double mass() {
            return mass;
        }
    }

    @Test
    void enumerationWithFields_keepsBoth() {
        UmlEntity entity = EntityExtractor.extract(Planet.class);

        assertEquals(ImmutableList.of("Earth", "MARCH"), entity.literals());
        assertEquals(ImmutableList.of("mass"),
            entity.fields().stream().map(UmlField::name).toList());
    }

    @UMLClass
    enum WithoutStrategy {
        A, B
    }

    @Test
    void noneStrategy_showsNoLiteral() {
        assertTrue(EntityExtractor.extract(WithoutStrategy.class).literals().isEmpty());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    enum WithExclusion {
        VISIBLE,

        @UMLExclude
        Internal
    }

    @Test
    void exclude_removesALiteral() {
        assertEquals(ImmutableList.of("VISIBLE"),
            EntityExtractor.extract(WithExclusion.class).literals());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    enum Operator {
        PLUS {
            @Override
            int apply(int a, int b) {
                return a + b;
            }
        },
        Less {
            @Override
            int apply(int a, int b) {
                return a - b;
            }
        };

        abstract int apply(int a, int b);
    }

    @Test
    void literalWithBody_isRecognized() {
        assertEquals(ImmutableList.of("PLUS", "Less"),
            EntityExtractor.extract(Operator.class).literals());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    enum NamedCurrency {
        @UMLInclude(name = "Euro")
        Euro,

        Dollar
    }

    @Test
    void aRenamedLiteral_appearsUnderTheDiagramName() {
        assertEquals(ImmutableList.of("Euro", "Dollar"),
            EntityExtractor.extract(NamedCurrency.class).literals());
    }

    @Test
    void aPatternFromTheRulesFile_reachesALiteral() {
        Rules rules = Rules.read("{ \"hide\": [\"Dol*\"] }", "umlg.json");

        assertEquals(ImmutableList.of("Euro"),
            EntityExtractor.extract(NamedCurrency.class, rules).literals());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Wallet {
        public Currency currency;
    }

    @Test
    void enumTypedField_staysAField() {
        UmlEntity entity = EntityExtractor.extract(Wallet.class);

        assertTrue(entity.literals().isEmpty());
        assertEquals(ImmutableList.of("currency"),
            entity.fields().stream().map(UmlField::name).toList());
    }
}

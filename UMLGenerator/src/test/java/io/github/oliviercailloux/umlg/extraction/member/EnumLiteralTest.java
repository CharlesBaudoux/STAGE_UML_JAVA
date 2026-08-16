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

// The literals of an enumeration.
//
// For reflection they are public static final fields; for UML,
// EnumerationLiteral, which carry their name only. These tests check that they leave the
// the field compartment without carrying away the real fields of the enumeration.
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
        // This is the fixed defect: without that sorting, Euro appeared as "+ {static} Currency
        // Euro", which is not the UML notation of a literal.
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
        // An enumeration can have real fields on top of its literals: both
        // compartments must coexist, each with its notation.
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
        // A literal stays subject to the class strategy, like any member:
        // "no strategy" means an empty box, literals included.
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
        // A constant with a body generates an anonymous subclass; the literal must stay
        // recognized as such despite that.
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
        // The annotation was read to decide the visibility of the literal, then forgotten for its
        // name: the author wrote @UMLInclude(name = ...), the processor accepted it, and the
        // the drawing kept Euro. A literal is named like any other member.
        assertEquals(ImmutableList.of("Euro", "Dollar"),
            EntityExtractor.extract(NamedCurrency.class).literals());
    }

    @Test
    void aPatternFromTheRulesFile_reachesALiteral() {
        // The only extraction path that did not receive the project rules: "hide EU*"
        // emptied every compartment but this one, with nothing documenting it.
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
        // isEnumConstant() holds only for the constants declared by the enumeration
        // itself: a field merely having that type is not concerned.
        UmlEntity entity = EntityExtractor.extract(Wallet.class);

        assertTrue(entity.literals().isEmpty());
        assertEquals(ImmutableList.of("currency"),
            entity.fields().stream().map(UmlField::name).toList());
    }
}

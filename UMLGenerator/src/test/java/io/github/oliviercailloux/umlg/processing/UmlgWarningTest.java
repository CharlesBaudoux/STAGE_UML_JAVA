package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgWarningTest {

    private static Verdict compiler(Path tmp, String body) throws IOException {
        return CompilationFixture.compileBody(tmp, body);
    }

    private static void assertWarns(Verdict verdict, String fragment) {
        assertTrue(verdict.successful(), "A warning must not stop javac: "
            + verdict.report());
        assertTrue(verdict.warningContains(fragment), verdict.report());
    }

    @Test
    void includeOnAnAlreadyVisibleField_staysSilentWithoutTheRules(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLInclude
                public String name;
            }
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void excludeOnAnAlreadyHiddenMember_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLExclude
                private String passedWord;
            }
            """);

        assertWarns(verdict, "@UMLExclude has no effect");
    }

    @Test
    void usefulExemption_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class MoteurDeRecherche {
                @UMLInclude
                private int counter;

                @UMLExclude
                public String trace;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void includeOnAPublicMethod_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLInclude
                public String getName() { return ""; }
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void excludeOnAnAssociation_warnsThatTheLinkVanishes(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Client {
                @UMLExclude
                @UMLAssociation(target = Order.class)
                private Order order;
            }
            """);

        assertWarns(verdict, "removes the link between the two boxes");
    }

    @Test
    void includeOnAnAssociation_warnsThatTheFieldDoesNotComeBack(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLInclude
                @UMLAssociation(target = Order.class)
                private Order order;
            }
            """);

        assertWarns(verdict, "never inside one");
    }

    @Test
    void strokeParametersUnderAttributeNotation_warn(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
                    notation = UMLNotation.ATTRIBUTE, type = UMLRelationType.COMPOSITION,
                    label = "passed")
                private List<Order> orders;
            }
            """);

        assertWarns(verdict, "decorate a path between two boxes");
        assertWarns(verdict, "type, label");
    }

    @Test
    void faultyStrokeParameterUnderAttributeNotation_isRefusedAndReported(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, notation = UMLNotation.ATTRIBUTE,
                    sourceMultiplicity = "3..1")
                private Order order;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("the lower bound exceeds the upper bound"),
            verdict.report());
        assertTrue(verdict.warningContains("decorate a path between two boxes"),
            "The uselessness of the parameter must be said together with its fault: "
                + verdict.report());
    }

    @Test
    void attributeNotationWithoutStrokeParameter_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
                    notation = UMLNotation.ATTRIBUTE)
                private List<Order> orders;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void excludeOnAnInBoxAssociation_warnsThatTheLineVanishes(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Client {
                @UMLExclude
                @UMLAssociation(target = Order.class, notation = UMLNotation.ATTRIBUTE)
                private Order order;
            }
            """);

        assertWarns(verdict, "removes the very line that association asked for");
    }

    @Test
    void includeOnAnInBoxAssociation_staysSilentWithoutTheRules(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLInclude
                @UMLAssociation(target = Order.class, notation = UMLNotation.ATTRIBUTE)
                private Order order;
            }
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void derivedOnAnInBoxAssociation_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLDerived
                @UMLAssociation(target = Order.class, notation = UMLNotation.ATTRIBUTE)
                private Order order;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void dependencyDuplicatingAnInBoxAssociation_saysNothing(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            @UMLDependency(target = Order.class, label = "invoice")
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
                    notation = UMLNotation.ATTRIBUTE)
                private List<Order> orders;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void multiplicityOnAHiddenMember_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Mnemonic {
                @UMLMultiplicity("0..*")
                private String alias;
            }
            """);

        assertWarns(verdict, "@UMLMultiplicity has no visible effect");
    }

    @Test
    void emptyMultiplicity_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Mnemonic {
                @UMLMultiplicity("")
                private String alias;
            }
            """);

        assertWarns(verdict, "asks for nothing");
    }

    @Test
    void visibleMultiplicity_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Mnemonic {
                @UMLMultiplicity("0..*")
                private String alias;

                @UMLMultiplicity("0..1")
                public String ucp() { return ""; }
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void alreadyFramedStereotype_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(stereotype = "<<Service>>")
            class Payment {}
            """);

        assertWarns(verdict, "already contains the quotes");
    }

    @Test
    void simpleStereotype_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(stereotype = "Service")
            class Payment {}
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void twoClassesWithTheSameDisplayedName_warn(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {}

            @UMLClass(name = "Client")
            class Acheteur {}
            """);

        assertWarns(verdict, "the reader cannot tell apart");
    }

    @Test
    void dependencyDuplicatingAnAssociation_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            @UMLDependency(target = Order.class, label = "invoice")
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*")
                private List<Order> orders;
            }
            """);

        assertWarns(verdict, "already links it to");
    }

    @Test
    void dependencyOnAnotherClass_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Log {}

            @UMLClass
            @UMLDependency(target = Log.class, label = "trace")
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*")
                private List<Order> orders;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void collectionWithoutMultiplicity_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class)
                private List<Order> orders;
            }
            """);

        assertWarns(verdict, "targetMultiplicity is not set");
    }

    @Test
    void collectionWithSingleValuedMultiplicity_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..1")
                private List<Order> orders;
            }
            """);

        assertWarns(verdict, "allows at most one object");
    }

    @Test
    void simpleFieldWithMultiValuedMultiplicity_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "1..*")
                private Order order;
            }
            """);

        assertWarns(verdict, "holds a single one");
    }

    @Test
    void arrayCountsAsCollection(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "1")
                private Order[] orders;
            }
            """);

        assertWarns(verdict, "allows at most one object");
    }

    @Test
    void multiplicityMatchingTheType_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*")
                private List<Order> orders;

                @UMLAssociation(target = Order.class, targetMultiplicity = "0..1")
                private Order last;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void targetAbsentFromTheFieldType_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Invoice {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..1")
                private Invoice invoice;
            }
            """);

        assertWarns(verdict, "does not appear anywhere in the field's type");
    }

    @Test
    void targetFoundDeepInAGeneric_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*")
                private Map<String, List<Order>> parReference;

                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*")
                private List<? extends Order> variantes;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void rawCollection_doesNotComplainAboutTheTarget(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*")
                private List orders;
            }
            """);

        assertTrue(verdict.warnings().stream()
            .noneMatch(message -> message.contains("does not appear anywhere")), verdict.report());
    }

    @Test
    void derivedOnAnUndisplayedMember_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Person {
                @UMLDerived
                private int age;
            }
            """);

        assertWarns(verdict, "@UMLDerived has no visible effect");
    }

    @Test
    void derivedOnAnExcludedMember_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Person {
                @UMLDerived
                @UMLExclude
                public int age;
            }
            """);

        assertWarns(verdict, "@UMLDerived has no visible effect");
    }

    @Test
    void derivedOnADisplayedMember_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Person {
                @UMLDerived
                private int age;

                @UMLDerived
                public String summarize() { return ""; }
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void derivedCaughtUpByAnInclude_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Person {
                @UMLDerived
                @UMLInclude
                private int age;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void derivedLinkWithoutTargetRole_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLDerived
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*")
                private List<Order> orders;
            }
            """);

        assertWarns(verdict, "targetRole is not set");
    }

    @Test
    void derivedLinkWithTargetRole_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLDerived
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
                    targetRole = "inProgress")
                private List<Order> orders;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void noteOnAnUndisplayedMember_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Account {
                @UMLNote(text = "Never negative")
                private double balance;
            }
            """);

        assertWarns(verdict, "nothing to hang on");
    }

    @Test
    void noteOnAFieldPromotedToLink_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Client {
                @UMLNote(text = "Lost")
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..1")
                private Order order;
            }
            """);

        assertWarns(verdict, "a note cannot hang on a link");
    }

    @Test
    void verticalDirectionOnAMemberNote_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Account {
                @UMLNote(text = "Never negative", direction = UMLDirection.DOWN)
                public double balance;
            }
            """);

        assertWarns(verdict, "direction = DOWN has no effect");
        assertWarns(verdict, "drawn on the right");
    }

    @Test
    void verticalDirectionOnAClassNote_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            @UMLNote(text = "Core of the domain", direction = UMLDirection.UP)
            class Account {
                public double balance;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void wellPlacedNotes_sayNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            @UMLNote(text = "Core of the domain")
            @UMLNote(text = "Reread the contract", direction = UMLDirection.LEFT)
            class Account {
                @UMLNote(text = "Never negative")
                public double balance;

                @UMLNote(text = "Algorithm critical", direction = UMLDirection.LEFT)
                public void check() {}
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void ruleAlreadyBetweenBraces_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLConstraint(rule = "{holder major}")
            class Account {}
            """);

        assertWarns(verdict, "already carries the braces");
    }

    @Test
    void constraintOnAnUndisplayedField_warns(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Account {
                @UMLConstraint(rule = "positive")
                private double balance;
            }
            """);

        assertWarns(verdict, "will not be written anywhere");
    }

    @Test
    void constraintOnAFieldPromotedToLink_saysNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLConstraint(rule = "subset")
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
                    targetRole = "purchases")
                private List<Order> orders;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void wellPlacedConstraints_sayNothing(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            @UMLConstraint(rule = "holder major")
            class Account {
                @UMLConstraint(rule = "balance >= 0")
                public double balance;
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void optionNone_silencesTheWarnings(@TempDir Path tmp) throws IOException {
        String body = """
            @UMLClass(stereotype = "<<Service>>")
            class Payment {}
            """;

        Verdict verbose = compiler(tmp, body);
        assertFalse(verbose.warnings().isEmpty(), "The witness must indeed warn.");

        Verdict silent = CompilationFixture.compileBody(tmp, body,
            List.of("-Aumlg.warnings=none"));

        assertTrue(silent.successful(), silent.report());
        assertTrue(silent.warnings().isEmpty(), silent.report());
    }

    @Test
    void optionNone_doesNotSilenceTheErrors(@TempDir Path tmp) throws IOException {
        Verdict verdict = CompilationFixture.compileBody(tmp, """
            @UMLClass(color = "NotAColor")
            class Client {}
            """, List.of("-Aumlg.warnings=none"));

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("Color unknown to PlantUML"), verdict.report());
    }

    @Test
    void optionWithAnUnknownValue_isRefused(@TempDir Path tmp) throws IOException {
        Verdict verdict = CompilationFixture.compileBody(tmp, """
            @UMLClass
            class Client {}
            """, List.of("-Aumlg.warnings=off"));

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("unknown value"), verdict.report());
    }

    @Test
    void optionWithoutValue_isRefused(@TempDir Path tmp) throws IOException {
        Verdict verdict = CompilationFixture.compileBody(tmp, """
            @UMLClass(stereotype = "<<Service>>")
            class Payment {}
            """, List.of("-Aumlg.warnings"));

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("no value"), verdict.report());
        assertTrue(verdict.errorContains("-Aumlg.warnings=none"), verdict.report());
    }
}

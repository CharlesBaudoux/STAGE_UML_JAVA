package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// What the processor lets compile, but not silently.
//
// The border with UmlgProcessorTest is that of severity, and it has one rule: an error
// says "this code describes no diagram", a warning says "this code describes a diagram,
// but probably not the one you think". Hence the assertion shared by all these tests:
// compilation succeeds. A warning that stopped javac would be a badly named error.
class UmlgWarningTest {

    // The header every fixture shares lives in CompilationFixture, with the harness.
    private static Verdict compiler(Path tmp, String body) throws IOException {
        return CompilationFixture.compileBody(tmp, body);
    }

    // Checks the severity together with the message: that is half of what each test says.
    private static void assertWarns(Verdict verdict, String fragment) {
        assertTrue(verdict.successful(), "A warning must not stop javac: "
            + verdict.report());
        assertTrue(verdict.warningContains(fragment), verdict.report());
    }

    // --- Exemptions that exempt nothing ---

    @Test
    void includeOnAnAlreadyVisibleField_staysSilentWithoutTheRules(@TempDir Path tmp) throws IOException {
        // The complaint "@UMLInclude is useless, the strategy already shows this field" depends on
        // two axes, not one: the strategy, which the class declares here in full, and the
        // the "hide" patterns of the rules file, which javac does not read without -Aumlg.rules. Under
        // "hide name", this @UMLInclude would be the only way of keeping the line, and removing it
        // as the processor advised erased the field from the diagram.
        //
        // The same code, with the option given, finds its warning again: see UmlgRulesOptionTest.
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
        // The very case of the "Inclusion exemption" section: a private member made visible under a
        // a strategy that would have hidden it. Nothing to report here, on pain of making the
        // warnings inaudibles.
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
        // A public method can be dropped by the noise filter — a getter — in which
        // case @UMLInclude is useful. The processor does not have that filter at hand: it stays silent rather
        // than complaining wrongly.
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLInclude
                public String getName() { return ""; }
            }
            """);

        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    // --- Exemptions set on an association ---

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

    // --- The association written in the box ---

    @Test
    void strokeParametersUnderAttributeNotation_warn(@TempDir Path tmp)
        throws IOException {
        // A field line has only a type and a multiplicity: the diamond, the role and the
        // label have nowhere to be written, and would vanish without a word.
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
        // The faulty parameters are named, and only they: targetMultiplicity, which is indeed
        // and rightly so, must not appear in the list.
        assertWarns(verdict, "type, label");
    }

    @Test
    void faultyStrokeParameterUnderAttributeNotation_isRefusedAndReported(@TempDir Path tmp)
        throws IOException {
        // The processor said two contrary things about the same parameter: "3..1" was refused there
        // as everywhere, and the next line announced that sourceMultiplicity would in any case
        // a way that is not drawn. The author fixed one value only to be told next to
        // l'effacer.
        //
        // It now says both together, and in that order: an impossible value stays
        // impossible — the generation refuses it too — and it is moreover useless here.
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
        // The message of the ARROW notation — "@UMLExclude removes the stroke" — would be wrong here:
        // there is no stroke, it is the line of the box that goes away.
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
        // "Already shown, whatever the strategy" was true of the strategy and false of the
        // the rules file: the noise filter drops that line too. Without the file,
        // the processor cannot know, and stays silent.
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
        // Under the ARROW notation, @UMLDerived without a targetRole has nothing to mark and the
        // the processor reports it. Here the slash sits before the name of the field, which
        // is written: there is nothing to complain about.
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
        // The association draws no stroke: the dependency is the only link of the drawing, it
        // n'encombre nothing.
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

    // --- The multiplicity that will not be seen ---

    @Test
    void multiplicityOnAHiddenMember_warns(@TempDir Path tmp) throws IOException {
        // Brackets show only if the member they follow is drawn.
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

    // --- The stereotype ---

    @Test
    void alreadyFramedStereotype_warns(@TempDir Path tmp) throws IOException {
        // The fault is invisible otherwise: nothing fails, the box merely displays four
        // angle brackets on each side.
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

    // --- Two indistinguishable boxes ---

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

    // --- The dependency duplicating an association ---

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

    // --- The multiplicity checked against the type ---

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

    // --- The target checked against the type ---

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
        // Map<String, List<Order>>: the target is two levels deep, and the
        // the search must go down that far rather than concluding too fast.
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
        // A bare "List" says nothing of what it holds: that is the exact gap
        // target() comes to fill, and complaining about it would amount to condemning the remedy.
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

    // --- Derived elements ---

    @Test
    void derivedOnAnUndisplayedMember_warns(@TempDir Path tmp) throws IOException {
        // The slash shows only if the member shows.
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
        // The counterpart of derivedCaughtUpByAnInclude: the question "will this member be drawn?"
        // consults both exemptions, and @UMLExclude removes a member the strategy
        // would show. The slash goes away with it.
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
        // @UMLInclude makes the member visible: the derivation finds a holder again, and the check
        // must take it into account rather than looking at the strategy only.
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
        // On an association, the slash sits before the target role. With no role, the property
        // is named nowhere and nothing can carry it.
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

    // --- Les notes ---

    @Test
    void noteOnAnUndisplayedMember_warns(@TempDir Path tmp) throws IOException {
        // The bubble would be drawn into the void: extraction drops it, and this is where it must
        // say it.
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
        // PlantUML places a member note on the left or on the right only. The rendering falls back on the
        // the right rather than writing a syntax that would get the whole document rejected; it remains to
        // tell the author that their direction was lost, where they wrote it.
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
        // All four sides do exist for a class: the warning must not
        // spill over onto it.
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

    // --- Les constraints ---

    @Test
    void ruleAlreadyBetweenBraces_warns(@TempDir Path tmp) throws IOException {
        // The same mistake as the stereotype quotes: UML's notation is so tied to the
        // braces that one writes them without thinking.
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
        // The field is not drawn, but the link is: the constraint leaves with it. That is
        // the arbitration rule of the specification, and the majority case.
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

    // --- Le silence on requested ---

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
        // An error says that no diagram can be produced: nobody can want to
        // be waived, and the option must not offer to do it by mistake.
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
        // "-Aumlg.warnings" on its own is the very typo that checking the values
        // claims to have fixed, and it went through the same hole: javac then files the key with
        // a null value, which the test "is the option missing?" confused with absence.
        // The user believed they had silenced the processor, and wondered why it
        // parlait encore.
        Verdict verdict = CompilationFixture.compileBody(tmp, """
            @UMLClass(stereotype = "<<Service>>")
            class Payment {}
            """, List.of("-Aumlg.warnings"));

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("no value"), verdict.report());
        // The message says what to write, and not merely what is wrong.
        assertTrue(verdict.errorContains("-Aumlg.warnings=none"), verdict.report());
    }
}

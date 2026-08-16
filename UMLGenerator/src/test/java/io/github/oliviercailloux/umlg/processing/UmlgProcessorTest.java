package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// What the processor refuses to let compile.
//
// Each test compiles a real file with a real javac: what is measured is therefore exactly
// what the user would get in their terminal, and not a simulation.
//
// The fixtures are written here as strings, and not as nested classes as
// elsewhere in the suite: faulty classes would not compile, and the test module with
// them. That is the accepted price of a processor doing its job well.
class UmlgProcessorTest {

    // The header every fixture shares lives in CompilationFixture, with the harness.
    private static Verdict compiler(Path tmp, String body) throws IOException {
        return CompilationFixture.compileBody(tmp, body);
    }

    // --- Le cas nominal : un code correct must passer ---

    @Test
    void correctCode_compilesWithoutError(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC, color = "LightBlue", stereotype = "Entity")
            class Order {}

            @UMLClass
            class ServiceBank {}

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            @UMLDependency(target = ServiceBank.class, label = "invoice via")
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
                    sourceMultiplicity = "1", targetRole = "purchases")
                private List<Order> orders;

                @UMLInclude
                private int counter;
            }
            """);

        assertTrue(verdict.successful(), verdict.report());
        // Correct code is silent code: not only without an error, but with nothing to
        // reporting. That is what keeps the warnings audible when there is one.
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    // --- The multiplicity of a member ---

    @Test
    void unreadableMultiplicity_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Mnemonic {
                @UMLMultiplicity("many")
                private String alias;
            }
            """);

        assertFalse(verdict.successful(), "\"many\" is not a multiplicity.");
        assertTrue(verdict.errorContains("valid UML multiplicity"), verdict.report());
    }

    @Test
    void multiplicityAndAssociationOnTheSameField_stopCompilation(@TempDir Path tmp)
        throws IOException {
        // Two multiplicities on the same property: the library could choose one, but
        // doing so silently would amount to deciding in place of the author.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLMultiplicity("0..*")
                @UMLAssociation(target = Order.class, targetMultiplicity = "1..*")
                private List<Order> orders;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("already given by targetMultiplicity"), verdict.report());
    }

    @Test
    void multiplicityOnAClassOutsideTheDiagram_stopsCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            class Mnemonic {
                @UMLMultiplicity("0..*")
                private String alias;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("does not carry @UMLClass"), verdict.report());
    }

    // --- La target d'one relation ---

    @Test
    void targetWithoutUmlClass_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class)
                private Order order;
            }
            """);

        assertFalse(verdict.successful(), "The target is not in the diagram.");
        assertTrue(verdict.errorContains("does not carry @UMLClass"), verdict.report());
        // The message must say what to do, not merely what is wrong.
        assertTrue(verdict.errorContains("Annotate it"), verdict.report());
    }

    @Test
    void primitiveTarget_stopsCompilation(@TempDir Path tmp) throws IOException {
        // int.class is legal Java: its type is Class<Integer>. Only the processor can
        // refusing what, for javac alone, is a perfectly valid expression.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLAssociation(target = int.class)
                private int age;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("primitive type"), verdict.report());
    }

    // --- Multiplicities ---

    @Test
    void reversedMultiplicity_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetMultiplicity = "3..1")
                private Order order;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("lower bound exceeds the upper bound"),
            verdict.report());
    }

    @Test
    void nonsensicalMultiplicity_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, sourceMultiplicity = "many")
                private Order order;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("valid UML multiplicity"), verdict.report());
        // The message comes from the model: it names the faulty parameter, which positioning
        // on the whole annotation would not say.
        assertTrue(verdict.errorContains("sourceMultiplicity"), verdict.report());
    }

    // --- La color ---

    @Test
    void colourUnknownToPlantUml_stopsCompilation(@TempDir Path tmp) throws IOException {
        // The form is valid — it is a word — only querying the real catalogue of
        // PlantUML may refuse it.
        Verdict verdict = compiler(tmp, """
            @UMLClass(color = "NotAColor")
            class Client {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("Color unknown to PlantUML"), verdict.report());
    }

    @Test
    void validColour_compiles(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(color = "#FF0000")
            class Rouge {}

            @UMLClass(color = "LightBlue")
            class Bleu {}
            """);

        assertTrue(verdict.successful(), verdict.report());
    }

    // --- Dependencies ---

    @Test
    void dependencyOnItself_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLDependency(target = Client.class)
            class Client {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("cannot depend on itself"), verdict.report());
    }

    @Test
    void duplicateDependency_stopsCompilation(@TempDir Path tmp) throws IOException {
        // The repeatable case: the compiler wraps both in @UMLDependencies, and the
        // the processor must be able to unwrap the container to see them.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Service {}

            @UMLClass
            @UMLDependency(target = Service.class, label = "pays")
            @UMLDependency(target = Service.class, label = "pays")
            class Client {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("declared twice with the same label"), verdict.report());
    }

    @Test
    void distinctRepeatedDependencies_compile(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Service {}

            @UMLClass
            class Log {}

            @UMLClass
            @UMLDependency(target = Service.class, label = "pays")
            @UMLDependency(target = Log.class, label = "trace")
            class Client {}
            """);

        assertTrue(verdict.successful(), verdict.report());
    }

    // --- Les contradictions ---

    @Test
    void includeAndExcludeTogether_stopCompilation(@TempDir Path tmp) throws IOException {
        // The library could decide — @UMLExclude wins — but applying a rule
        // silently would amount to choosing in place of the author of contradictory code.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude
                @UMLExclude
                private String name;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("contradictory"), verdict.report());
    }

    // --- Annotations without effect ---

    @Test
    void associationOnAClassWithoutUmlClass_stopsCompilation(@TempDir Path tmp)
        throws IOException {
        // The forgotten @UMLClass: the annotation is correct in itself, but would produce nothing.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            class Client {
                @UMLAssociation(target = Order.class)
                private Order order;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("drawn from nowhere"), verdict.report());
    }

    @Test
    void includeOnAClassWithoutUmlClass_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            class Client {
                @UMLInclude
                private String name;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("has no effect"), verdict.report());
    }

    @Test
    void constraintWithAnEmptyRule_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLConstraint(rule = "  ")
            class Account {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("cannot be empty"), verdict.report());
    }

    @Test
    void constraintOnAMethod_isRefusedByJavacAlone(@TempDir Path tmp) throws IOException {
        // A constraint bears on a state, not on a call: @Target forbids it, and it is the
        // the compiler that says so.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Account {
                @UMLConstraint(rule = "positive")
                public void credit() {}
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
    }

    @Test
    void noteWithEmptyText_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = "   ")
            class Client {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("cannot be empty"), verdict.report());
    }

    @Test
    void noteOnAClassWithoutUmlClass_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLNote(text = "Without box")
            class Client {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("nothing to hang on"), verdict.report());
    }

    @Test
    void derivedOnAClassWithoutUmlClass_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            class Person {
                @UMLDerived
                private int age;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("has no effect"), verdict.report());
    }

    @Test
    void derivedOnAClass_isRefusedByJavacAlone(@TempDir Path tmp) throws IOException {
        // The notion of a derived class does not exist in UML: @Target forbids it, and it is the
        // the compiler that says so, without the processor having to step in.
        Verdict verdict = compiler(tmp, """
            @UMLDerived
            @UMLClass
            class Person {}
            """);

        assertFalse(verdict.successful(), verdict.report());
    }

    @Test
    void dependencyOnAClassWithoutUmlClass_stopsCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Service {}

            @UMLDependency(target = Service.class)
            class Client {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("drawn from nowhere"), verdict.report());
    }

    // --- Les texts empty ---

    @Test
    void nameMadeOfSpaces_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(name = "   ")
            class Client {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("whitespace only"), verdict.report());
    }

    @Test
    void roleMadeOfSpaces_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Order.class, targetRole = "  ")
                private Order order;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("whitespace only"), verdict.report());
    }

    // --- The target inferred from the field type: the processor must say what the
    // generation, no more and no less ---

    @Test
    void associationWithoutTarget_towardsATypeOutsideTheDiagram_stopsCompilation(@TempDir Path tmp)
        throws IOException {
        // This test locked a design choice that no longer exists: target() had no
        // a default value, and it was javac alone that refused the annotation omitting it. The
        // the parameter now carries "default void.class" — the "nothing written" sentinel, which
        // triggers inference from the field type. The test went on passing, but
        // on a pattern altogether different from the one it announced.
        //
        // What it now keeps: inference does not loosen the check. "private String
        // name" does give a target — String — and that target is refused like any
        // another class outside the diagram. The sentinel opens the door to the field type, not
        // to the first type that comes.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLAssociation(targetMultiplicity = "1")
                private String name;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("target java.lang.String does not carry @UMLClass"),
            verdict.report());
        // And the remedy names the parameter to write, rather than merely stating the fact.
        assertTrue(verdict.errorContains("point target() at a class that is"), verdict.report());
    }

    @Test
    void associationOnATypeVariableField_compiles(@TempDir Path tmp) throws IOException {
        // The processor stopped the compilation of code the generation drew without
        // flinching: it held "T" as undetermined, where reflection reads the erasure,
        // that is, the bound. It is the costliest gap of the two halves, since it blocks
        // the user rather than letting them see a questionable drawing.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Order {}

            @UMLClass
            class Box<T extends Order> {
                @UMLAssociation(targetMultiplicity = "1")
                private T content;
            }
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void rawCollectionWithoutTarget_namesTheFaultyType(@TempDir Path tmp) throws IOException {
        // The message now comes from InferredTarget, to which the rule belongs: the one from
        // the processor did not name the type, and a project with several associations left its
        // author to find which one was meant.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLAssociation(targetMultiplicity = "0..*")
                private List orders;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("the field's type (List) does not say what it contains"),
            verdict.report());
    }

    @Test
    void primitiveFieldWithoutTarget_namesTheFaultyType(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLAssociation(targetMultiplicity = "1")
                private int counter;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(
            verdict.errorContains("the field's type (int) does not name a class of the diagram"),
            verdict.report());
    }

    @Test
    void emptyConstraint_reusesTheModelMessage(@TempDir Path tmp) throws IOException {
        // The rule is no longer rewritten here: javac's refusal is word for word the one the
        // the generation would have produced. The two texts had already diverged.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLConstraint(rule = " ")
            class Account {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains(
            "A constraint cannot be empty or made of whitespace only."), verdict.report());
    }
}

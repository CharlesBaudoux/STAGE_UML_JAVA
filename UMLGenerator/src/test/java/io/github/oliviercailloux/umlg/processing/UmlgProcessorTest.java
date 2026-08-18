package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgProcessorTest {

    private static Verdict compiler(Path tmp, String body) throws IOException {
        return CompilationFixture.compileBody(tmp, body);
    }

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
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

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
        assertTrue(verdict.errorContains("Annotate it"), verdict.report());
    }

    @Test
    void primitiveTarget_stopsCompilation(@TempDir Path tmp) throws IOException {
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
        assertTrue(verdict.errorContains("sourceMultiplicity"), verdict.report());
    }

    @Test
    void colourUnknownToPlantUml_stopsCompilation(@TempDir Path tmp) throws IOException {
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

    @Test
    void includeAndExcludeTogether_stopCompilation(@TempDir Path tmp) throws IOException {
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

    @Test
    void associationOnAClassWithoutUmlClass_stopsCompilation(@TempDir Path tmp)
        throws IOException {
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

    @Test
    void associationWithoutTarget_towardsATypeOutsideTheDiagram_stopsCompilation(@TempDir Path tmp)
        throws IOException {
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
        assertTrue(verdict.errorContains("point target() at a class that is"), verdict.report());
    }

    @Test
    void associationOnATypeVariableField_compiles(@TempDir Path tmp) throws IOException {
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

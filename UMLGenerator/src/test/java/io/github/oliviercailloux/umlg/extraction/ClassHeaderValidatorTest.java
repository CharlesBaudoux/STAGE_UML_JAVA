package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

// Exercises the border between the two validation regimes of a class header: allow-list
// blank for the colour, the minimal constraint for texts read by a human.
//
// The test is in the same package as ClassHeaderValidator: that is what gives it access to
// this class, which is not public.
//
// getClass() serves as the faulty class in the messages: which one does not matter, only the
// the fact that there is one.
class ClassHeaderValidatorTest {

    // --- Colour: a closed vocabulary, hence an allow-list ---

    @Test
    void invalidColour_isRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateColor("not one color !", getClass()),
            "A value that is neither a hexadecimal code nor a colour name must be rejected.");
    }

    @Test
    void namedColour_isAccepted() {
        // The colour vocabulary is closed, but it is not limited to hexadecimal:
        // "LightBlue" is a perfectly valid PlantUML colour, which the old
        // a regular expression refused.
        assertDoesNotThrow(() -> ClassHeaderValidator.validateColor("LightBlue", getClass()));
    }

    // --- Name and stereotype: free texts, hence a minimal constraint ---

    @Test
    void nameWithSpecialCharacters_isAccepted() {
        // These names are legitimate and rendered faithfully: refusing them would make
        // on the user a constraint that concerns the rendering engine only.
        assertDoesNotThrow(() -> {
            ClassHeaderValidator.validateName("Name\"Injected", getClass());
            ClassHeaderValidator.validateName("Account (bank)", getClass());
            ClassHeaderValidator.validateName("The User", getClass());
            ClassHeaderValidator.validateName("Client<T>", getClass());
        });
    }

    @Test
    void blankName_isRejected() {
        // The only rule left, and it is a business one: a name must designate something.
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateName("   ", getClass()),
            "A name made of spaces only must be rejected.");
    }

    // The angle brackets are not forbidden here: they will be encoded at rendering time by
    // PlantUmlEscaper.stereotype, and will be displayed as is.
    @Test
    void stereotypeWithAngleBrackets_isAccepted() {
        assertDoesNotThrow(() -> ClassHeaderValidator.validateStereotype("<<Hack>>", getClass()));
    }

    @Test
    void blankStereotype_isRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateStereotype("   ", getClass()));
    }

    // --- What escaping empties, validation must refuse ---
    //
    // isBlank() does not treat control characters as blanks, whereas escaping
    // removes them: a name made of those characters alone crossed validation, then generated
    // "class \"\" as C" — which PlantUML refuses. The writer's check then raised an
    // an IllegalStateException blaming "a defect of the library", a message the user
    // could not follow. The question is therefore asked of the escaped text, the one that will be written.

    @Test
    void nameMadeOfInvisibleCharacters_isRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateName("\1\2", getClass()),
            "This name comes down to the empty string in the generated file.");
    }

    @Test
    void stereotypeMadeOfInvisibleCharacters_isRejected() {
        // Its own manifestation: "class \"E\" as E <<>>", which the engine also refuses.
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateStereotype("\1\2", getClass()));
    }

    @Test
    void ordinaryNameWithAControlCharacter_staysAccepted() {
        // The fix does not harden the rule: it is enough that something is left to draw.
        assertDoesNotThrow(() -> ClassHeaderValidator.validateName("Account\1", getClass()));
        assertDoesNotThrow(() -> ClassHeaderValidator.validateStereotype("entity\1", getClass()));
    }
}

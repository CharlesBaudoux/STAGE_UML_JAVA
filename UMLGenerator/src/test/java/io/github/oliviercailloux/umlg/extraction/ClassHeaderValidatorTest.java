package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ClassHeaderValidatorTest {

    @Test
    void invalidColour_isRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateColor("not one color !", getClass()),
            "A value that is neither a hexadecimal code nor a colour name must be rejected.");
    }

    @Test
    void namedColour_isAccepted() {
        assertDoesNotThrow(() -> ClassHeaderValidator.validateColor("LightBlue", getClass()));
    }

    @Test
    void nameWithSpecialCharacters_isAccepted() {
        assertDoesNotThrow(() -> {
            ClassHeaderValidator.validateName("Name\"Injected", getClass());
            ClassHeaderValidator.validateName("Account (bank)", getClass());
            ClassHeaderValidator.validateName("The User", getClass());
            ClassHeaderValidator.validateName("Client<T>", getClass());
        });
    }

    @Test
    void blankName_isRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateName("   ", getClass()),
            "A name made of spaces only must be rejected.");
    }

    @Test
    void stereotypeWithAngleBrackets_isAccepted() {
        assertDoesNotThrow(() -> ClassHeaderValidator.validateStereotype("<<Hack>>", getClass()));
    }

    @Test
    void blankStereotype_isRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateStereotype("   ", getClass()));
    }

    @Test
    void nameMadeOfInvisibleCharacters_isRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateName("\1\2", getClass()),
            "This name comes down to the empty string in the generated file.");
    }

    @Test
    void stereotypeMadeOfInvisibleCharacters_isRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateStereotype("\1\2", getClass()));
    }

    @Test
    void ordinaryNameWithAControlCharacter_staysAccepted() {
        assertDoesNotThrow(() -> ClassHeaderValidator.validateName("Account\1", getClass()));
        assertDoesNotThrow(() -> ClassHeaderValidator.validateStereotype("entity\1", getClass()));
    }
}

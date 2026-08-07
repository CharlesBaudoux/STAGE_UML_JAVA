package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import org.junit.jupiter.api.Test;

// La nature de l'entité, déduite du mot-clé Java.
//
// L'enjeu de ces tests est l'ordre des questions posées à la réflexion : les catégories de Java
// se recouvrent, et chaque cobaye ci-dessous appartient à plusieurs d'entre elles à la fois.
class EntityKindTest {

    private static UmlEntityKind natureDe(Class<?> clazz) {
        return EntityExtractor.extract(clazz).kind();
    }

    @UMLClass
    static class Compte {}

    @Test
    void classeOrdinaire_estUneClasse() {
        assertEquals(UmlEntityKind.CLASS, natureDe(Compte.class));
    }

    @UMLClass
    abstract static class Operation {}

    @Test
    void classeAbstraite_estReconnue() {
        assertEquals(UmlEntityKind.ABSTRACT_CLASS, natureDe(Operation.class));
    }

    @UMLClass
    interface Virement {}

    @Test
    void interface_estReconnueAvantLAbstraction() {
        assertEquals(UmlEntityKind.INTERFACE, natureDe(Virement.class));
    }

    @UMLClass
    enum Devise {
        EURO, DOLLAR
    }

    @Test
    void enumeration_estReconnue() {
        assertEquals(UmlEntityKind.ENUM, natureDe(Devise.class));
    }

    @UMLClass
    enum Operateur {
        PLUS {
            @Override
            int appliquer(int a, int b) {
                return a + b;
            }
        };

        abstract int appliquer(int a, int b);
    }

    @Test
    void enumerationAbstraite_resteUneEnumeration() {
        assertEquals(UmlEntityKind.ENUM, natureDe(Operateur.class));
    }

    @UMLClass
    @interface Auditable {}

    @Test
    void typeAnnotation_estReconnuAvantLInterface() {
        assertEquals(UmlEntityKind.ANNOTATION, natureDe(Auditable.class));
    }

    @UMLClass
    record Montant(double valeur, String devise) {}

    @Test
    void record_estUneClasse() {
        assertEquals(UmlEntityKind.CLASS, natureDe(Montant.class));
    }
}

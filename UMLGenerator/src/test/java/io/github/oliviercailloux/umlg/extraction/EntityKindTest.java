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
        // Une interface porte toujours le modificateur abstract : si le test d'abstraction
        // passait en premier, toutes les interfaces deviendraient des classes abstraites.
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
        // Une constante à corps fait de l'énumération une classe abstraite pour le compilateur.
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
        // Le cobaye le plus révélateur : Operateur est à la fois enum et abstract. Le test sur
        // l'énumération doit donc précéder celui sur l'abstraction.
        assertEquals(UmlEntityKind.ENUM, natureDe(Operateur.class));
    }

    @UMLClass
    @interface Auditable {}

    @Test
    void typeAnnotation_estReconnuAvantLInterface() {
        // Un @interface est une interface pour la réflexion : sans test préalable, il serait
        // dessiné comme telle, et l'information serait perdue.
        assertEquals(UmlEntityKind.ANNOTATION, natureDe(Auditable.class));
    }

    @UMLClass
    record Montant(double valeur, String devise) {}

    @Test
    void record_estUneClasse() {
        // Décision assumée : un record est une classe finale, et UML ne lui réserve aucune
        // notation. Le dessiner en classe n'est pas un pis-aller, c'est exact.
        assertEquals(UmlEntityKind.CLASS, natureDe(Montant.class));
    }
}

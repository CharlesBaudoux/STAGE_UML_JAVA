package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

// L'en-tête d'une boîte générique, et l'italique des opérations abstraites.
//
// Deux manques qui se ressemblaient : la réflexion sait les deux choses, et le modèle n'avait
// aucune composante pour les dire. Une « Boite<T> » s'affichait « Boite », avec un attribut de
// type T que plus rien ne définissait ; et dans une classe abstraite, un contrat se dessinait
// exactement comme une implémentation.
class EnTeteGeneriqueTest {

    @UMLClass
    static class Boite<T> {
        public T contenu;
    }

    @UMLClass
    static class Table<K, V extends Number> {}

    @UMLClass
    static class Ordinaire {}

    @UMLClass
    abstract static class Contrat {
        public abstract void valider();

        public void ecrite() {}
    }

    @UMLClass
    interface Signature {
        void obligatoire();

        default void fournie() {}
    }

    private static UmlEntity extraite(Class<?> classe) {
        return EntityExtractor.extract(classe, Regles.lues(
            com.google.common.collect.ImmutableList.of("strategy public"), "umlg.rules"));
    }

    @Test
    void uneClasseGenerique_porteSonParametreDeType() {
        assertEquals("<T>", extraite(Boite.class).typeParameters());
    }

    @Test
    void plusieursParametres_sEcriventDansLOrdreEtAvecLeursBornes() {
        assertEquals("<K, V extends Number>", extraite(Table.class).typeParameters());
    }

    @Test
    void uneClasseOrdinaire_nEnPorteAucun() {
        // La chaîne vide, et non null : l'en-tête ne doit rien gagner sur une classe qui n'est
        // pas générique.
        assertEquals("", extraite(Ordinaire.class).typeParameters());
    }

    @Test
    void leParametreDeType_neChangePasLeNomDeLaBoite() {
        // Le nom sert à désigner la boîte — une vue la nomme ainsi, l'alias s'en déduit. Coller
        // les chevrons dedans rendrait « view "X" = Boite » introuvable.
        assertEquals("Boite", extraite(Boite.class).name());
    }

    private static UmlMethod methode(Class<?> classe, String nom) {
        return extraite(classe).methods().stream()
            .filter(methode -> methode.name().equals(nom))
            .findFirst().orElseThrow();
    }

    @Test
    void uneMethodeSansCorps_estDiteAbstraite() {
        assertTrue(methode(Contrat.class, "valider").isAbstract());
    }

    @Test
    void uneMethodeEcrite_neLEstPas() {
        assertFalse(methode(Contrat.class, "ecrite").isAbstract());
    }

    @Test
    void uneMethodeDInterface_estAbstraiteSansPorterLeMotCle() {
        assertTrue(methode(Signature.class, "obligatoire").isAbstract());
    }

    @Test
    void uneMethodeDefault_neLEstPas() {
        // « default » est justement ce qui distingue, dans une interface, le contrat de ce qui
        // est fourni : le modificateur ne ment pas, contrairement au mot-clé absent.
        assertFalse(methode(Signature.class, "fournie").isAbstract());
    }

    @Test
    void leModeleOrdinaire_construitDesMethodesConcretes() {
        // Le raccourci à sept arguments, celui qu'emploient les tests de rendu : l'abstraction
        // est l'exception, et ne pas la dire veut dire qu'il n'y en a pas.
        UmlMethod methode = new UmlMethod("faire", "void", "+", false, List.of());

        assertFalse(methode.isAbstract());
    }
}

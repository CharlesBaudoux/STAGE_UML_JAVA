package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlNotationTest {

    @UMLClass
    static class Commande {}


    @UMLClass(name = "Defaut")
    static class Defaut {
        @UMLAssociation(target = Commande.class, targetMultiplicity = "0..*")
        private List<Commande> commandes;
    }

    @UMLClass(name = "DansLaBoite")
    static class DansLaBoite {
        @UMLAssociation(target = Commande.class, targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> commandes;
    }

    private static String document(Class<?>... classes) {
        UmlEntity[] entites = new UmlEntity[classes.length];
        for (int i = 0; i < classes.length; i++) {
            entites[i] = EntityExtractor.extract(classes[i]);
        }
        return PlantUmlAssert.documentPour(entites);
    }


    @Test
    void sansNotation_leLienResteUnTrait() {
        String source = document(Defaut.class, Commande.class);

        assertEquals(List.of("Defaut --> \"0..*\" Commande"), PlantUmlAssert.lignesDeLien(source));
        assertFalse(source.contains("commandes"),
            "L'attribut a quitté la boîte au profit du trait :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }


    @Test
    void notationAttribute_ecritLaLigneDansLaBoite() {
        String source = document(DansLaBoite.class, Commande.class);

        assertTrue(source.contains("- commandes : Commande [0..*]"),
            "La ligne attendue est absente :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void notationAttribute_neDessineAucunTrait() {
        String source = document(DansLaBoite.class, Commande.class);

        assertEquals(List.of(), PlantUmlAssert.lignesDeLien(source),
            "Une propriété ne s'affiche jamais deux fois :\n" + source);
        assertEquals(2, PlantUmlAssert.aliasDeclares(source).size(), source);
    }

    @Test
    void lecteurVoitLaMultiplicite() throws Exception {
        String texte = PlantUmlAssert.texteRendu(EntityExtractor.extract(DansLaBoite.class),
            EntityExtractor.extract(Commande.class));

        assertTrue(texte.contains("[0..*]"),
            "La multiplicité doit survivre au dessin, et non rester dans le fichier source :\n"
                + texte);
    }

    @UMLClass
    static class SansMultiplicite {
        @UMLAssociation(target = Commande.class, notation = UMLNotation.ATTRIBUTE)
        private Commande commande;
    }

    @Test
    void sansMultiplicite_aucunCrochetNEstEcrit() {
        String source = document(SansMultiplicite.class, Commande.class);

        assertTrue(source.contains("- commande : Commande\n"),
            "Une multiplicité vide vaut « exactement un » et ne s'écrit pas :\n" + source);
        assertFalse(source.contains("[]"), source);
    }


    @UMLClass
    static class Decoree {
        @UMLConstraint(rule = "ordered")
        @UMLConstraint(rule = "non vide")
        @UMLDerived
        @UMLAssociation(target = Commande.class, targetMultiplicity = "1..*",
            notation = UMLNotation.ATTRIBUTE)
        protected List<Commande> achats;
    }

    @Test
    void barreObliqueMultipliciteEtContraintes_seSuiventDansLOrdreUml() {
        String source = document(Decoree.class, Commande.class);

        assertTrue(source.contains("# /achats : Commande [1..*] {ordered} {non vide}"),
            "La ligne attendue est absente :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }


    @UMLClass(name = "Clavier")
    static class Clavier {
        @UMLNote(text = "Doublons autorises")
        @UMLAssociation(target = Commande.class, targetRole = "physicalKeys",
            targetMultiplicity = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Commande> keys;
    }

    @Test
    void roleCible_nommeLaPropriete() {
        String source = document(Clavier.class, Commande.class);

        assertTrue(source.contains("- physicalKeys : Commande [0..*]"),
            "Le rôle doit nommer la ligne :\n" + source);
        assertFalse(source.contains(" keys "), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurUnePropriete_saccrocheAuNomAffiche() {
        String source = document(Clavier.class, Commande.class);

        assertTrue(source.contains("note right of Clavier::physicalKeys"),
            "La note doit viser le nom affiché :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }


    @UMLClass(strategy = UMLVisibility.NONE)
    static class StrategieMuette {
        @UMLAssociation(target = Commande.class, targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> commandes;
    }

    @Test
    void strategieNone_neMasquePasUneAssociationDemandee() {
        String source = document(StrategieMuette.class, Commande.class);

        assertTrue(source.contains("- commandes : Commande [0..*]"), source);
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Retiree {
        @UMLExclude
        @UMLAssociation(target = Commande.class, targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> commandes;
    }

    @Test
    void exclude_retireLaLigneCommeIlRetireraitLeTrait() {
        String source = document(Retiree.class, Commande.class);

        assertFalse(source.contains("commandes"), source);
        assertEquals(List.of(), PlantUmlAssert.lignesDeLien(source), source);
    }


    static class HorsDiagramme {}

    static class CibleNonAnnotee {
        @UMLAssociation(target = HorsDiagramme.class, notation = UMLNotation.ATTRIBUTE)
        private HorsDiagramme champ;
    }

    @Test
    void cibleSansUmlClass_estRefuseeCommePourUnTrait() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(CibleNonAnnotee.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("@UMLClass"), e.getMessage());
    }

    static class MultipliciteFautive {
        @UMLAssociation(target = Commande.class, targetMultiplicity = "3..1",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> commandes;
    }

    @Test
    void multipliciteInvalide_estRefuseeEtNommeLAttribut() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(MultipliciteFautive.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("commandes"),
            "Le message doit désigner l'attribut fautif : " + e.getMessage());
    }


    @UMLClass
    static class Melangee {
        @UMLAssociation(target = Commande.class, targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> archivees;

        @UMLAssociation(target = Commande.class, targetMultiplicity = "1", targetRole = "courante")
        private Commande courante;
    }

    @Test
    void deuxNotations_coexistentSurLaMemeClasse() {
        String source = document(Melangee.class, Commande.class);

        assertTrue(source.contains("- archivees : Commande [0..*]"), source);
        assertEquals(List.of("Melangee --> \"courante 1\" Commande"),
            PlantUmlAssert.lignesDeLien(source), source);
        PlantUmlAssert.assertCompile(source);
    }
}

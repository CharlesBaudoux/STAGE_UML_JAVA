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

// Le choix entre les deux notations d'une association : le trait, ou la ligne dans la boîte.
//
// Ces tests partent tous des annotations et vont jusqu'au document, parce que c'est précisément
// le chemin que notation() détourne : le même @UMLAssociation doit produire un trait d'un côté,
// une ligne d'attribut de l'autre, et jamais les deux — la règle UML qui interdit d'afficher deux
// fois la même propriété vaut quelle que soit la notation choisie.
class PlantUmlNotationTest {

    @UMLClass
    static class Commande {}

    // La classe de départ, déclinée une fois par cas. Elles se ressemblent : c'est le paramètre
    // qui change, et lui seul.

    @UMLClass(name = "Defaut")
    static class Defaut {
        @UMLAssociation(target = Commande.class, targetCardinality = "0..*")
        private List<Commande> commandes;
    }

    @UMLClass(name = "DansLaBoite")
    static class DansLaBoite {
        @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
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

    // --- Le défaut ---

    @Test
    void sansNotation_leLienResteUnTrait() {
        String source = document(Defaut.class, Commande.class);

        assertEquals(List.of("Defaut --> \"0..*\" Commande"), PlantUmlAssert.lignesDeLien(source));
        assertFalse(source.contains("commandes"),
            "L'attribut a quitté la boîte au profit du trait :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    // --- La notation ATTRIBUTE ---

    @Test
    void notationAttribute_ecritLaLigneDansLaBoite() {
        String source = document(DansLaBoite.class, Commande.class);

        // Le type affiché est la cible, et non « List » : c'est la multiplicité qui dit qu'il y
        // en a plusieurs, comme le veut UML.
        assertTrue(source.contains("- commandes: Commande [0..*]"),
            "La ligne attendue est absente :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void notationAttribute_neDessineAucunTrait() {
        String source = document(DansLaBoite.class, Commande.class);

        assertEquals(List.of(), PlantUmlAssert.lignesDeLien(source),
            "Une propriété ne s'affiche jamais deux fois :\n" + source);
        // Les deux boîtes restent : la cible ne disparaît pas avec le trait.
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

        assertTrue(source.contains("- commande: Commande\n"),
            "Une multiplicité vide vaut « exactement un » et ne s'écrit pas :\n" + source);
        assertFalse(source.contains("[]"), source);
    }

    // --- Ce que la ligne porte en plus ---

    @UMLClass
    static class Decoree {
        @UMLConstraint(rule = "non vide")
        @UMLDerived
        @UMLAssociation(target = Commande.class, targetCardinality = "1..*",
            isOrdered = true, notation = UMLNotation.ATTRIBUTE)
        protected List<Commande> achats;
    }

    @Test
    void barreObliqueMultipliciteEtContraintes_seSuiventDansLOrdreUml() {
        // L'ordre est celui d'UML, et le même que sur un trait : le nom, sa multiplicité, puis
        // les règles — {ordered} avant celles de l'utilisateur.
        String source = document(Decoree.class, Commande.class);

        assertTrue(source.contains("# /achats: Commande [1..*] {ordered} {non vide}"),
            "La ligne attendue est absente :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    // --- Le rôle, qui devient le nom de la propriété ---

    @UMLClass(name = "Clavier")
    static class Clavier {
        @UMLNote(text = "Doublons autorises")
        @UMLAssociation(target = Commande.class, targetRole = "physicalKeys",
            targetCardinality = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Commande> keys;
    }

    @Test
    void roleCible_nommeLaPropriete() {
        // En notation attribut, le rôle cible est le nom que l'on lit dans la boîte : c'est ce
        // que dit UML, et c'est ce qu'écrivent les diagrammes tenus à la main — « physicalKeys »
        // là où le code déclare un champ « keys ».
        String source = document(Clavier.class, Commande.class);

        assertTrue(source.contains("- physicalKeys: Commande [0..*]"),
            "Le rôle doit nommer la ligne :\n" + source);
        assertFalse(source.contains(" keys "), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurUnePropriete_saccrocheAuNomAffiche() {
        // Une bulle désigne un membre par le nom que PlantUML lui connaît ; viser le nom du
        // champ Java accrocherait la note à un membre inexistant.
        String source = document(Clavier.class, Commande.class);

        assertTrue(source.contains("note right of Clavier::physicalKeys"),
            "La note doit viser le nom affiché :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    // --- Les dérogations et la stratégie ---

    @UMLClass(strategy = UMLVisibility.NONE)
    static class StrategieMuette {
        @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> commandes;
    }

    @Test
    void strategieNone_neMasquePasUneAssociationDemandee() {
        // Sous la notation ARROW, le trait se dessine quelle que soit la stratégie : une demande
        // explicite l'emporte sur un défaut. La notation ATTRIBUTE suit la même règle, sans quoi
        // le même code dirait deux choses selon la façon de le dessiner.
        String source = document(StrategieMuette.class, Commande.class);

        assertTrue(source.contains("- commandes: Commande [0..*]"), source);
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Retiree {
        @UMLExclude
        @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> commandes;
    }

    @Test
    void exclude_retireLaLigneCommeIlRetireraitLeTrait() {
        String source = document(Retiree.class, Commande.class);

        assertFalse(source.contains("commandes"), source);
        assertEquals(List.of(), PlantUmlAssert.lignesDeLien(source), source);
    }

    // --- Ce que la notation ne relâche pas ---
    //
    // Ces cobayes ne portent volontairement pas @UMLClass, et l'extraction des membres est
    // appelée directement : le balayage de target/test-classes par les tests d'intégration les
    // extrairait sinon, et échouerait sur des fautes qui sont ici l'objet du test.

    static class HorsDiagramme {}

    static class CibleNonAnnotee {
        @UMLAssociation(target = HorsDiagramme.class, notation = UMLNotation.ATTRIBUTE)
        private HorsDiagramme champ;
    }

    @Test
    void cibleSansUmlClass_estRefuseeCommePourUnTrait() {
        // La notation choisit une forme de dessin ; elle ne fait pas de n'importe quelle classe
        // une classe du diagramme.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(CibleNonAnnotee.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("@UMLClass"), e.getMessage());
    }

    static class MultipliciteFautive {
        @UMLAssociation(target = Commande.class, targetCardinality = "3..1",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> commandes;
    }

    @Test
    void multipliciteInvalide_estRefuseeEtNommeLAttribut() {
        // La même grammaire que sur un trait, et le même refus : « 3..1 » ne décrit rien.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(MultipliciteFautive.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("commandes"),
            "Le message doit désigner l'attribut fautif : " + e.getMessage());
    }

    // --- Les deux notations dans le même diagramme ---

    @UMLClass
    static class Melangee {
        @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> archivees;

        @UMLAssociation(target = Commande.class, targetCardinality = "1", targetRole = "courante")
        private Commande courante;
    }

    @Test
    void deuxNotations_coexistentSurLaMemeClasse() {
        String source = document(Melangee.class, Commande.class);

        assertTrue(source.contains("- archivees: Commande [0..*]"), source);
        assertEquals(List.of("Melangee --> \"courante 1\" Commande"),
            PlantUmlAssert.lignesDeLien(source), source);
        PlantUmlAssert.assertCompile(source);
    }
}

package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import org.junit.jupiter.api.Test;

// L'extraction des associations : son articulation avec le compartiment des attributs, la
// reprise des paramètres de l'annotation, et la qualité des messages d'erreur.
//
// La résolution de la cible, elle, a son propre test (TargetResolverTest).
class AssociationExtractorTest {

    // Deux cibles possibles ; elles doivent porter @UMLClass pour être visables.
    @UMLClass
    static class Commande {}

    @UMLClass
    static class Ligne {}

    // Extrait l'unique association attendue, en échouant avec un message utile s'il y en a zéro
    // ou plusieurs. Évite de répéter la même vérification dans chaque test.
    private static UmlAssociation seuleAssociation(Class<?> clazz) {
        List<UmlAssociation> associations = EntityExtractor.extract(clazz).associations();
        assertEquals(1, associations.size(),
            () -> "Attendu exactement une association sur " + clazz.getSimpleName()
                + ", obtenu : " + associations);
        return associations.get(0);
    }

    // --- Articulation avec les attributs et les dérogations ---

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class ChampPromu {
        @UMLAssociation(target = Commande.class)
        private Commande commande;
        private String reference;
    }

    @Test
    void attributPromuEnAssociation_quitteLeCompartimentDesAttributs() {
        // En UML une propriété se note dans la boîte ou comme un lien, jamais les deux.
        UmlEntity entity = EntityExtractor.extract(ChampPromu.class);

        assertEquals(List.of("reference"), entity.fields().stream().map(UmlField::name).toList());
        assertEquals(1, entity.associations().size());
    }

    @UMLClass
    static class ChampPriveSansStrategie {
        @UMLAssociation(target = Commande.class)
        private Commande commande;
    }

    @Test
    void associationSurChampInvisible_estQuandMemeDessinee() {
        // La stratégie filtre le contenu des boîtes, pas les liens entre elles : une
        // composition entre deux classes du modèle ne disparaît pas parce que le champ qui la
        // porte est privé — c'est le cas le plus courant.
        UmlEntity entity = EntityExtractor.extract(ChampPriveSansStrategie.class);

        assertTrue(entity.fields().isEmpty());
        assertEquals(1, entity.associations().size());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class ChampExclu {
        @UMLAssociation(target = Commande.class)
        @UMLExclude
        private Commande commande;
    }

    @Test
    void umlExclude_faitTaireLAssociationEtLAttribut() {
        UmlEntity entity = EntityExtractor.extract(ChampExclu.class);

        assertTrue(entity.associations().isEmpty(), "@UMLExclude doit supprimer le lien.");
        assertTrue(entity.fields().isEmpty(),
            "L'attribut promu ne doit pas réapparaître dans la boîte.");
    }

    @UMLClass
    static class SansAssociation {
        private Commande commande;
    }

    @Test
    void sansAnnotation_aucuneAssociationNEstDeduite() {
        // La déduction automatique des liens n'est pas du ressort de cette annotation : un
        // attribut nu reste un attribut.
        assertTrue(EntityExtractor.extract(SansAssociation.class).associations().isEmpty());
    }

    // --- Paramètres ---

    @UMLClass
    static class ExempleDeLaSpecification {
        @UMLAssociation(
            target = Ligne.class,
            type = UMLRelationType.COMPOSITION,
            sourceMultiplicity = "1",
            targetMultiplicity = "0..*",
            targetRole = "achats",
            label = "passe",
            sourceRole = "client",
            direction = UMLDirection.DOWN)
        private List<Ligne> lignes;
    }

    @Test
    void tousLesParametres_sontRepercutesDansLeModele() {
        UmlAssociation association = seuleAssociation(ExempleDeLaSpecification.class);

        assertEquals("lignes", association.fieldName());
        assertEquals(Ligne.class.getName(), association.targetQualifiedName());
        assertEquals(UMLRelationType.COMPOSITION, association.type());
        assertEquals("1", association.sourceMultiplicity());
        assertEquals("0..*", association.targetMultiplicity());
        assertEquals("client", association.sourceRole());
        assertEquals("achats", association.targetRole());
        assertEquals("passe", association.label());
        assertEquals(UMLDirection.DOWN, association.direction());
    }

    @UMLClass
    static class AnnotationNue {
        @UMLAssociation(target = Commande.class)
        private Commande commande;
    }

    // Le pendant du test précédent : hormis target, obligatoire, tout doit valoir « rien » —
    // chaîne vide, NONE — et jamais null, que le rendu aurait à tester partout.
    @Test
    void parDefaut_touteValeurFacultativeEstNeutre() {
        UmlAssociation association = seuleAssociation(AnnotationNue.class);

        assertEquals(UMLRelationType.ASSOCIATION, association.type());
        assertEquals(UMLDirection.NONE, association.direction());
        assertEquals("", association.sourceMultiplicity());
        assertEquals("", association.targetMultiplicity());
        assertEquals("", association.sourceRole());
        assertEquals("", association.targetRole());
        assertEquals("", association.label());
        assertTrue(association.constraints().isEmpty());
    }

    // --- Le « où » ajouté au « quoi » ---

    // Sans @UMLClass, volontairement : le test appelle l'extracteur directement, sans passer par
    // EntityExtractor qui, lui, exigerait l'annotation.
    static class ChampMultipliciteInvalide {
        @UMLAssociation(target = Commande.class, targetMultiplicity = "beaucoup")
        private Commande commande;
    }

    @Test
    void multipliciteInvalide_estRefuseeEnNommantSonEmplacement() {
        // Sur un projet de quarante classes, « "beaucoup" n'est pas une multiplicité » est un
        // message qui ne mène nulle part : l'extraction y ajoute le « où ».
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new AssociationExtractor().extraire(ChampMultipliciteInvalide.class));

        assertTrue(e.getMessage().contains("commande")
                && e.getMessage().contains("targetMultiplicity"),
            () -> "Message trop pauvre pour situer la faute : " + e.getMessage());
    }

    @Test
    void listeDAssociations_estImmuable() {
        List<UmlAssociation> associations =
            EntityExtractor.extract(AnnotationNue.class).associations();

        assertThrows(UnsupportedOperationException.class, () -> associations.clear());
    }
}

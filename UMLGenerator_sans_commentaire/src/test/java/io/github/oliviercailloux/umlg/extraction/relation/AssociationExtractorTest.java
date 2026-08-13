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

class AssociationExtractorTest {

    @UMLClass
    static class Commande {}

    @UMLClass
    static class Ligne {}

    private static UmlAssociation seuleAssociation(Class<?> clazz) {
        List<UmlAssociation> associations = EntityExtractor.extract(clazz).associations();
        assertEquals(1, associations.size(),
            () -> "Attendu exactement une association sur " + clazz.getSimpleName()
                + ", obtenu : " + associations);
        return associations.get(0);
    }


    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class ChampPromu {
        @UMLAssociation(target = Commande.class)
        private Commande commande;
        private String reference;
    }

    @Test
    void attributPromuEnAssociation_quitteLeCompartimentDesAttributs() {
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
        assertTrue(EntityExtractor.extract(SansAssociation.class).associations().isEmpty());
    }


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


    static class ChampMultipliciteInvalide {
        @UMLAssociation(target = Commande.class, targetMultiplicity = "beaucoup")
        private Commande commande;
    }

    @Test
    void multipliciteInvalide_estRefuseeEnNommantSonEmplacement() {
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

package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityExtractorTest {

    @UMLClass(name = "NomPersonnalise", stereotype = "Entity", color = "AABBCC")
    static class ClasseSansMembres {}

    @UMLClass(color = "#ABC")
    static class CouleurHexCourte {}

    @UMLClass
    static class SansMetadonnees {
        public String champPublic;
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class ClasseAvecMembres {
        public String champPublic;
    }

    static class ClasseNonAnnotee {}

    @Test
    void nomStereotypeEtCouleur_sontRepercutesSurLEntite() {
        UmlEntity entity = EntityExtractor.extract(ClasseSansMembres.class);

        assertEquals("NomPersonnalise", entity.name());
        assertEquals("Entity", entity.stereotype());
        assertEquals("AABBCC", entity.color());
        assertTrue(entity.fields().isEmpty());
        assertTrue(entity.methods().isEmpty());
    }

    @Test
    void nomParDefaut_estLeSimpleNameQuandNonSurcharge() {
        UmlEntity entity = EntityExtractor.extract(SansMetadonnees.class);

        assertEquals("SansMetadonnees", entity.name());
    }

    @Test
    void nomQualifie_estLIdentiteDeLEntite() {
        UmlEntity entity = EntityExtractor.extract(ClasseSansMembres.class);

        assertEquals(ClasseSansMembres.class.getName(), entity.qualifiedName());
    }

    @Test
    void couleurHexCourte_estAcceptee() {
        UmlEntity entity = EntityExtractor.extract(CouleurHexCourte.class);
        assertEquals("#ABC", entity.color(),
            "Le format hexadécimal à 3 chiffres doit être accepté.");
    }

    @UMLClass
    static class Cible {}

    @UMLClass
    @UMLDependency(target = Cible.class, label = "appelle")
    static class PorteLesDeuxNaturesDeLiens {
        @UMLAssociation(target = Cible.class)
        private Cible detenue;
    }

    @Test
    void toutesLesNaturesDeLiens_arriventDansLaMemeListe() {
        List<UmlRelation> relations =
            EntityExtractor.extract(PorteLesDeuxNaturesDeLiens.class).relations();

        assertEquals(2, relations.size(), () -> "Les deux liens sont attendus : " + relations);
        assertEquals(1, relations.stream().filter(r -> r instanceof UmlAssociation).count());
        assertEquals(1, relations.stream().filter(r -> r instanceof UmlDependency).count());
    }

    @Test
    void lOrdreDesLiens_suitCeluiDuCatalogue() {
        List<UmlRelation> relations =
            EntityExtractor.extract(PorteLesDeuxNaturesDeLiens.class).relations();

        assertTrue(relations.get(0) instanceof UmlAssociation, relations.toString());
        assertTrue(relations.get(1) instanceof UmlDependency, relations.toString());
    }

    @Test
    void extractionEchoueSiLaClasseNEstPasAnnotee() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityExtractor.extract(ClasseNonAnnotee.class));
    }

    @Test
    void extractionEchoueSurNull() {
        assertThrows(NullPointerException.class, () -> EntityExtractor.extract(null));
    }

    @Test
    void champsSontListesImmuables() {
        UmlEntity entity = EntityExtractor.extract(ClasseAvecMembres.class);

        assertThrows(UnsupportedOperationException.class, () -> entity.fields().add(null));
    }

    @Test
    void deuxExtractionsDeLaMemeClasse_sontEgales() {
        assertEquals(EntityExtractor.extract(ClasseAvecMembres.class),
            EntityExtractor.extract(ClasseAvecMembres.class),
            "UmlEntity est un record : deux extractions identiques doivent être égales.");
    }
}

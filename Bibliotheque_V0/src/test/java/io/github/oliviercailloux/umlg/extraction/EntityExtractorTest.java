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

// Le contrat propre de l'orchestrateur : lire l'en-tête @UMLClass, et appeler tous les
// extracteurs de relations.
//
// Les classes de démonstration sont déclarées ici même, en static nested : elles sont annotées
// comme le ferait un utilisateur, et l'extraction travaillant par réflexion, cela suffit. Aucun
// fichier d'exemple séparé n'est nécessaire, et chaque test a sous les yeux le code qu'il lit.
class EntityExtractorTest {

    @UMLClass(name = "NomPersonnalise", stereotype = "Entity", color = "AABBCC")
    static class ClasseSansMembres {}

    @UMLClass(color = "#ABC")
    static class CouleurHexCourte {}

    // Sans strategy : le défaut est NONE, donc champPublic n'apparaîtra pas dans la boîte.
    @UMLClass
    static class SansMetadonnees {
        public String champPublic;
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class ClasseAvecMembres {
        public String champPublic;
    }

    static class ClasseNonAnnotee {}

    // --- Métadonnées de la classe ---

    @Test
    void nomStereotypeEtCouleur_sontRepercutesSurLEntite() {
        UmlEntity entity = EntityExtractor.extract(ClasseSansMembres.class);

        assertEquals("NomPersonnalise", entity.name());
        assertEquals("Entity", entity.stereotype());
        // La couleur est reprise telle quelle, sans le '#' : l'ajouter est l'affaire du rendu.
        assertEquals("AABBCC", entity.color());
        // Aucune stratégie déclarée, donc aucun membre — même si la classe en avait.
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
        // L'étiquette est libre, donc ni unique ni reliée au code ; c'est le nom qualifié qui
        // permet à un lien de désigner sa cible sans ambiguïté.
        UmlEntity entity = EntityExtractor.extract(ClasseSansMembres.class);

        assertEquals(ClasseSansMembres.class.getName(), entity.qualifiedName());
    }

    @Test
    void couleurHexCourte_estAcceptee() {
        UmlEntity entity = EntityExtractor.extract(CouleurHexCourte.class);
        assertEquals("#ABC", entity.color(),
            "Le format hexadécimal à 3 chiffres doit être accepté.");
    }

    // --- Le catalogue d'extracteurs de liens ---

    // Cible d'une association et d'une dépendance ; elle doit porter @UMLClass, sinon
    // TargetResolver refuserait de la viser.
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
        // Le contrat propre de l'orchestrateur : chaque extracteur du catalogue est appelé, et
        // ses liens rejoignent les autres. Un extracteur oublié se verrait ici, et nulle part
        // ailleurs — chacun des tests spécialisés ne connaît que sa propre nature.
        List<UmlRelation> relations =
            EntityExtractor.extract(PorteLesDeuxNaturesDeLiens.class).relations();

        // Message sous forme de lambda : la chaîne n'est construite que si le test échoue.
        assertEquals(2, relations.size(), () -> "Les deux liens sont attendus : " + relations);
        assertEquals(1, relations.stream().filter(r -> r instanceof UmlAssociation).count());
        assertEquals(1, relations.stream().filter(r -> r instanceof UmlDependency).count());
    }

    @Test
    void lOrdreDesLiens_suitCeluiDuCatalogue() {
        // Les associations avant les dépendances : l'ordre du catalogue est celui dans lequel
        // les traits seront écrits, donc une décision de rendu reproductible.
        List<UmlRelation> relations =
            EntityExtractor.extract(PorteLesDeuxNaturesDeLiens.class).relations();

        assertTrue(relations.get(0) instanceof UmlAssociation, relations.toString());
        assertTrue(relations.get(1) instanceof UmlDependency, relations.toString());
    }

    // --- Garde-fous ---

    @Test
    void extractionEchoueSiLaClasseNEstPasAnnotee() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityExtractor.extract(ClasseNonAnnotee.class));
    }

    @Test
    void extractionEchoueSurNull() {
        assertThrows(NullPointerException.class, () -> EntityExtractor.extract(null));
    }

    // add(null) plutôt qu'un vrai UmlField : la liste immuable refuse l'ajout avant même de
    // regarder ce qu'on lui donne.
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

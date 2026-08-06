package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

// L'extraction des dépendances : la répétabilité de l'annotation, et les fautes que
// l'extracteur refuse de laisser passer en silence.
//
// À noter : les classes fautives des tests de refus ne portent pas @UMLClass, et les tests
// appellent DependencyExtractor directement. C'est voulu — sans quoi le scan de
// target/test-classes, dans les tests d'intégration, les rencontrerait et échouerait.
class DependencyExtractorTest {

    @UMLClass
    static class ServiceBancaire {}

    @UMLClass
    static class Journal {}

    // Cible interdite : pas de @UMLClass, donc pas dans le diagramme.
    static class HorsDiagramme {}

    // --- Cas nominal ---

    @UMLClass
    @UMLDependency(target = ServiceBancaire.class, label = "facture via")
    static class Panier {}

    @Test
    void dependanceSimple_estExtraite() {
        UmlEntity entity = EntityExtractor.extract(Panier.class);

        assertEquals(
            ImmutableList.of(new UmlDependency(ServiceBancaire.class.getName(), "facture via")),
            entity.dependencies());
    }

    @UMLClass
    @UMLDependency(target = ServiceBancaire.class)
    static class SansMotif {}

    @Test
    void labelOmis_laisseLeLienNu() {
        assertEquals("", EntityExtractor.extract(SansMotif.class).dependencies().get(0).label());
    }

    @UMLClass
    @UMLDependency(target = ServiceBancaire.class, label = "paie")
    @UMLDependency(target = Journal.class, label = "trace")
    static class DeuxDependances {}

    @Test
    void annotationRepetee_produitAutantDeLiens() {
        // Une classe ne dépend presque jamais d'une seule autre : l'annotation doit être
        // répétable, sans quoi il faudrait choisir laquelle des dépendances montrer.
        List<UmlDependency> dependances =
            EntityExtractor.extract(DeuxDependances.class).dependencies();

        assertEquals(2, dependances.size());
        assertEquals(
            List.of(ServiceBancaire.class.getName(), Journal.class.getName()),
            dependances.stream().map(UmlDependency::targetQualifiedName).toList());
    }

    @UMLClass
    @UMLDependency(target = ServiceBancaire.class, label = "paie")
    @UMLDependency(target = ServiceBancaire.class, label = "rembourse")
    static class MemeCibleDeuxMotifs {}

    @Test
    void memeCibleAvecDesMotifsDifferents_estAcceptee() {
        // Deux traits parallèles portant deux étiquettes distinctes : le lecteur les voit, et
        // ils disent chacun quelque chose.
        assertEquals(2, EntityExtractor.extract(MemeCibleDeuxMotifs.class).dependencies().size());
    }

    @UMLClass
    static class AucuneDependance {}

    @Test
    void sansAnnotation_aucuneDependanceNEstDeduite() {
        assertTrue(EntityExtractor.extract(AucuneDependance.class).dependencies().isEmpty());
    }

    // --- Fautes silencieuses ---

    @UMLDependency(target = ServiceBancaire.class, label = "paie")
    @UMLDependency(target = ServiceBancaire.class, label = "paie")
    static class DoublonExact {}

    @Test
    void dependanceDeclareeDeuxFoisALIdentique_estRefusee() {
        // Deux traits rigoureusement superposés : rien ne se voit sur le diagramme, et c'est
        // toujours le signe d'un copier-coller plutôt que d'une intention.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extraire(DoublonExact.class));

        assertTrue(e.getMessage().contains("twice"),
            () -> "Le message doit nommer la faute : " + e.getMessage());
    }

    @UMLDependency(target = AutoDependance.class)
    static class AutoDependance {}

    @Test
    void classeDependantDElleMeme_estRefusee() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extraire(AutoDependance.class));

        assertTrue(e.getMessage().contains("depend on itself"),
            () -> "Le message doit nommer la faute : " + e.getMessage());
    }

    @UMLDependency(target = HorsDiagramme.class)
    static class CibleNonAnnotee {}

    @Test
    void cibleSansUmlClass_estRefusee() {
        // Sans ce refus, PlantUML inventerait une boîte vide portant ce nom : un diagramme
        // faux, indiscernable d'une classe réellement sans membre.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extraire(CibleNonAnnotee.class));

        assertTrue(e.getMessage().contains("@UMLClass") && e.getMessage().contains("target()"),
            () -> "Le message doit expliquer ce qui manque et comment y remédier : "
                + e.getMessage());
    }

    // int.class est un Class valide côté Java, mais ne désigne aucune boîte possible.
    @UMLDependency(target = int.class)
    static class CiblePrimitive {}

    @Test
    void ciblePrimitive_estRefusee() {
        assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extraire(CiblePrimitive.class));
    }

    @UMLDependency(target = ServiceBancaire.class, label = "   ")
    static class LabelBlanc {}

    @Test
    void labelBlanc_estRefuseEnNommantLaClasse() {
        // Le « quoi » vient du modèle, le « où » de l'extraction.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extraire(LabelBlanc.class));

        assertTrue(e.getMessage().contains("LabelBlanc"),
            () -> "Le message doit situer la faute : " + e.getMessage());
    }

    @Test
    void listeDeDependances_estImmuable() {
        List<UmlDependency> dependances = EntityExtractor.extract(Panier.class).dependencies();

        assertThrows(UnsupportedOperationException.class, () -> dependances.clear());
    }
}

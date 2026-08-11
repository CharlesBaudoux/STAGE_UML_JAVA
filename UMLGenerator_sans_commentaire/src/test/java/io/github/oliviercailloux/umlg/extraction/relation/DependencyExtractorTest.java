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

class DependencyExtractorTest {

    @UMLClass
    static class ServiceBancaire {}

    @UMLClass
    static class Journal {}

    static class HorsDiagramme {}

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
        assertEquals(2, EntityExtractor.extract(MemeCibleDeuxMotifs.class).dependencies().size());
    }

    @UMLClass
    static class AucuneDependance {}

    @Test
    void sansAnnotation_aucuneDependanceNEstDeduite() {
        assertTrue(EntityExtractor.extract(AucuneDependance.class).dependencies().isEmpty());
    }

    @UMLDependency(target = ServiceBancaire.class, label = "paie")
    @UMLDependency(target = ServiceBancaire.class, label = "paie")
    static class DoublonExact {}

    @Test
    void dependanceDeclareeDeuxFoisALIdentique_estRefusee() {
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
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extraire(CibleNonAnnotee.class));

        assertTrue(e.getMessage().contains("@UMLClass") && e.getMessage().contains("target()"),
            () -> "Le message doit expliquer ce qui manque et comment y remédier : "
                + e.getMessage());
    }

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

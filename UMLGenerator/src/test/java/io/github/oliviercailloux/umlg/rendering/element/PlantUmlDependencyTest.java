package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// Le rendu des dépendances, construit sur le même plan que PlantUmlAssociationTest : notation,
// acceptation par le moteur, injection par le label, résolution de la cible, puis un aller
// complet depuis les annotations.
//
// Le point propre à ce fichier : dépendance et association doivent rester visuellement
// distinctes (pointillé contre plein) alors qu'elles relient les deux mêmes boîtes.
class PlantUmlDependencyTest {

    @UMLClass
    static class ServiceBancaire {}

    private static UmlEntity cible(String nomQualifie) {
        return UmlEntity.builder(nomQualifie, "Cible").build();
    }

    private static UmlEntity[] paire(UmlDependency dependance) {
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(dependance)).build();
        return new UmlEntity[] {source, cible(dependance.targetQualifiedName())};
    }

    private static List<String> lignesDeLien(String source) {
        return PlantUmlAssert.lignesDeLien(source);
    }

    // Isole l'unique ligne de lien attendue, avec un message parlant si le compte diffère.
    private static String ligneUnique(String source) {
        List<String> lignes = lignesDeLien(source);
        assertEquals(1, lignes.size(), () -> "Attendu un seul lien dans :\n" + source);
        return lignes.get(0);
    }

    // --- Notation ---

    @Test
    void dependance_estUnTraitPointilleFleche() {
        // Le pointillé est ce qui distingue, à la lecture, un simple usage d'un lien
        // structurel : « ..> » et non « --> ».
        assertEquals("Source ..> Cible",
            ligneUnique(PlantUmlAssert.documentPour(paire(UmlDependency.toTarget("Cible")))));
    }

    @Test
    void label_suitLesDeuxPoints() {
        assertEquals("Source ..> Cible : facture via",
            ligneUnique(PlantUmlAssert.documentPour(
                paire(new UmlDependency("Cible", "facture via")))));
    }

    @Test
    void dependanceEtAssociation_coexistentSansSeConfondre() {
        // Les deux natures de liens partent de la même classe : elles doivent produire deux
        // traits distincts, l'un plein, l'autre pointillé.
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .associations(ImmutableList.of(UmlAssociation.builder("champ", "Cible").build()))
            .dependencies(ImmutableList.of(UmlDependency.toTarget("Cible")))
            .build();

        String rendu = PlantUmlAssert.documentPour(source, cible("Cible"));

        assertEquals(List.of("Source --> Cible", "Source ..> Cible"), lignesDeLien(rendu));
        PlantUmlAssert.assertCompile(rendu);
    }

    @Test
    void plusieursDependances_produisentAutantDeTraits() {
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(
                new UmlDependency("A", "paie"), new UmlDependency("B", "trace")))
            .build();

        String rendu = PlantUmlAssert.documentPour(
            source, UmlEntity.builder("A", "A").build(), UmlEntity.builder("B", "B").build());

        assertEquals(List.of("Source ..> A : paie", "Source ..> B : trace"), lignesDeLien(rendu));
        PlantUmlAssert.assertCompile(rendu);
    }

    // --- Le moteur accepte-t-il vraiment cela ? ---

    static List<UmlDependency> dependancesVariees() {
        return List.of(
            UmlDependency.toTarget("Cible"),
            new UmlDependency("Cible", "utilise"),
            new UmlDependency("Cible", "n'appelle qu'en lecture"),
            new UmlDependency("Cible", "Réf. n°42"));
    }

    @ParameterizedTest
    @MethodSource("dependancesVariees")
    void dependanceRendue_estAccepteeParLeMoteur(UmlDependency dependance) {
        String source = PlantUmlAssert.documentPour(paire(dependance));

        PlantUmlAssert.assertCompile(source);
        assertEquals(2, PlantUmlAssert.aliasDeclares(source).size(),
            () -> "Le lien a fait apparaître ou disparaître une boîte :\n" + source);
    }

    // --- Injection par le label ---

    static List<String> labelsHostiles() {
        return List.of(
            "fin\" as X\nclass Intruse",
            "<img:/etc/hostname>",
            "@enduml\n@startuml\nclass Intruse",
            "}\nclass Intruse {",
            "Source ..> Intruse",
            "C:\\Users\\moi");
    }

    @ParameterizedTest
    @MethodSource("labelsHostiles")
    void labelHostile_neCasseNiLeDocumentNiLeNombreDeBoites(String label) {
        String source = PlantUmlAssert.documentPour(paire(new UmlDependency("Cible", label)));

        assertEquals(1, PlantUmlAssert.nombreDeDiagrammes(source),
            () -> "Le label hostile a ouvert un second diagramme :\n" + source);
        assertEquals(2, PlantUmlAssert.aliasDeclares(source).size(),
            () -> "Le label hostile a déclaré une classe supplémentaire :\n" + source);
        assertEquals(1, lignesDeLien(source).size(),
            () -> "Le label hostile a terminé la ligne de lien :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void labelQuelconque_estAfficheTelQuel() throws Exception {
        String rendu =
            PlantUmlAssert.texteRendu(paire(new UmlDependency("Cible", "facture (n°1)")));

        assertTrue(rendu.contains("facture (n°1)"),
            () -> "Le label n'est pas rendu fidèlement. Texte dessiné :\n" + rendu);
    }

    // --- Résolution de la cible ---

    @Test
    void cibleAbsenteDuDiagramme_estUneErreurFranche() {
        UmlEntity solitaire = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(UmlDependency.toTarget("Cible"))).build();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentPour(solitaire));

        assertTrue(e.getMessage().contains("Cible") && e.getMessage().contains("dependency"),
            () -> "Le message doit désigner le lien fautif : " + e.getMessage());
    }

    @Test
    void cibleResolueSurLeNomQualifie_etNonSurLeNomAffiche() {
        UmlEntity homonyme = cible("pkg.a.Cible");
        UmlEntity visee = cible("pkg.b.Cible");
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(UmlDependency.toTarget("pkg.b.Cible"))).build();

        String rendu = PlantUmlAssert.documentPour(homonyme, visee, source);
        List<String> alias = PlantUmlAssert.aliasDeclares(rendu);

        // « homonyme » est déclarée en premier, elle garde donc l'alias non suffixé ; le lien
        // doit viser l'autre.
        assertEquals("Source ..> " + alias.get(1), ligneUnique(rendu));
        PlantUmlAssert.assertCompile(rendu);
    }

    // --- Bout en bout, depuis les annotations ---

    @UMLClass
    @UMLDependency(target = ServiceBancaire.class, label = "facture via")
    static class Panier {
        @UMLAssociation(target = ServiceBancaire.class, targetMultiplicity = "0..1")
        private ServiceBancaire dernierPaiement;
    }

    @Test
    void exempleDeLaSpecification_estRenduEtAccepte() {
        String source = PlantUmlAssert.documentPour(
            EntityExtractor.extract(Panier.class), EntityExtractor.extract(ServiceBancaire.class));

        assertEquals(
            List.of("Panier --> \"0..1\" ServiceBancaire",
                "Panier ..> ServiceBancaire : facture via"),
            lignesDeLien(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void dependancesDeclarees_apparaissentApresToutesLesClasses() {
        String source = PlantUmlAssert.documentPour(
            EntityExtractor.extract(Panier.class), EntityExtractor.extract(ServiceBancaire.class));

        assertTrue(source.indexOf("..>") > source.lastIndexOf("class \""),
            () -> "Un lien cité avant la déclaration de sa cible ferait apparaître une boîte "
                + "vide :\n" + source);
    }
}

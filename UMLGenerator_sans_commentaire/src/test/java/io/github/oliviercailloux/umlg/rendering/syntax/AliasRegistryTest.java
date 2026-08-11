package io.github.oliviercailloux.umlg.rendering.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

class AliasRegistryTest {

    @Test
    void deuxHomonymes_recoiventDesAliasDistincts() {
        AliasRegistry registre = new AliasRegistry();

        String premier = registre.enregistrer("a.Client", "Client");
        String second = registre.enregistrer("b.Client", "Client");

        org.junit.jupiter.api.Assertions.assertNotEquals(premier, second);
    }

    @UMLClass(name = "Mon.Client")
    static class NomAvecPoint {}

    @UMLClass(name = "MonClient")
    static class NomSansPoint {}

    @UMLClass(name = "MonClient")
    static class NomSansPointBis {}

    @UMLClass(name = "客户")
    static class NomIdeogrammes {}

    @UMLClass(name = "Réseau")
    static class NomAccentue {}

    @UMLClass(name = "...")
    static class NomPonctuationSeule {}

    @UMLClass(name = "42")
    static class NomNumerique {}

    @UMLClass(name = "Service De Paiement")
    static class NomAvecEspaces {}

    @Test
    void nomEnIdeogrammes_produitUnAliasUtilisable() {
        String source = PlantUmlAssert.documentPour(EntityExtractor.extract(NomIdeogrammes.class));

        assertEquals(List.of("客户"), PlantUmlAssert.nomsDeclares(source),
            "Le nom affiché doit rester l'unicode d'origine.");
        assertAliasNonVide(source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void nomEnPonctuationSeule_produitUnAliasUtilisable() {
        String source =
            PlantUmlAssert.documentPour(EntityExtractor.extract(NomPonctuationSeule.class));

        assertAliasNonVide(source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void nomNumerique_produitUnAliasNeCommencantPasParUnChiffre() {
        String source = PlantUmlAssert.documentPour(EntityExtractor.extract(NomNumerique.class));

        String alias = PlantUmlAssert.aliasDeclares(source).get(0);
        assertTrue(Character.isLetter(alias.charAt(0)),
            "Un alias commençant par un chiffre n'est pas un identifiant sûr : " + alias);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void nomAvecEspaces_produitUnAliasSansEspace() {
        String source = PlantUmlAssert.documentPour(EntityExtractor.extract(NomAvecEspaces.class));

        String alias = PlantUmlAssert.aliasDeclares(source).get(0);
        assertEquals("ServiceDePaiement", alias);
        assertEquals(List.of("Service De Paiement"), PlantUmlAssert.nomsDeclares(source),
            "Le nom affiché doit conserver ses espaces.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void nomAccentue_conserveSesLettresAsciiDansLAlias() {
        String source = PlantUmlAssert.documentPour(EntityExtractor.extract(NomAccentue.class));

        assertAliasNonVide(source);
        assertEquals(List.of("Réseau"), PlantUmlAssert.nomsDeclares(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void deuxNomsDifferentsSeReduisantAuMemeAlias_reste2ClassesDistinctes() {
        String source = PlantUmlAssert.documentPour(
            EntityExtractor.extract(NomAvecPoint.class),
            EntityExtractor.extract(NomSansPoint.class));

        List<String> alias = PlantUmlAssert.aliasDeclares(source);

        assertEquals(2, alias.size(), "Les deux classes doivent être déclarées.");
        assertNotEquals(alias.get(0), alias.get(1),
            "Deux entités distinctes partageant un alias sont fusionnées silencieusement par "
                + "PlantUML.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memeNomAffichePourDeuxEntites_resteDeuxClassesDistinctes() {
        UmlEntity a = EntityExtractor.extract(NomSansPoint.class);
        UmlEntity b = EntityExtractor.extract(NomSansPointBis.class);

        String source = PlantUmlAssert.documentPour(a, b);
        List<String> alias = PlantUmlAssert.aliasDeclares(source);

        assertEquals(2, alias.size());
        assertNotEquals(alias.get(0), alias.get(1),
            "Deux entités rendues séparément doivent recevoir deux alias distincts.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void aliasDUnNomDejaPropre_resteLeNomLuiMeme() {
        String source = PlantUmlAssert.documentPour(EntityExtractor.extract(NomSansPoint.class));

        assertEquals(List.of("MonClient"), PlantUmlAssert.aliasDeclares(source));
        PlantUmlAssert.assertCompile(source);
    }

    private static void assertAliasNonVide(String source) {
        List<String> alias = PlantUmlAssert.aliasDeclares(source);
        assertEquals(1, alias.size(), "Une seule classe attendue dans :\n" + source);
        assertTrue(!alias.get(0).isBlank(),
            "Un alias vide casse la déclaration PlantUML :\n" + source);
    }
}

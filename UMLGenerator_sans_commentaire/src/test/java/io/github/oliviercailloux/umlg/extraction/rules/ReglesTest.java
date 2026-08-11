package io.github.oliviercailloux.umlg.extraction.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReglesTest {

    private static Regles regles(String... lignes) {
        return Regles.lues(ImmutableList.copyOf(lignes), "umlg.rules");
    }

    @Test
    void nomSimple_estRemplace() {
        assertEquals("List", regles("type ImmutableList -> List").nomAffiche(ImmutableList.class));
    }

    @Test
    void nomComplet_estRemplace() {
        assertEquals("List",
            regles("type com.google.common.collect.ImmutableList -> List")
                .nomAffiche(ImmutableList.class));
    }

    @Test
    void nomComplet_lEmporteSurLeNomSimple() {
        Regles regles = regles(
            "type ImmutableList -> Collection",
            "type com.google.common.collect.ImmutableList -> List");

        assertEquals("List", regles.nomAffiche(ImmutableList.class));
    }

    @Test
    void typeSansRegle_gardeSonNomSimple() {
        assertEquals("String", regles("type ImmutableList -> List").nomAffiche(String.class));
    }

    @Test
    void commentairesEtLignesVides_sontIgnores() {
        Regles regles = regles(
            "# Les types de Guava n'apprennent rien au lecteur.",
            "",
            "   ",
            "   type ImmutableSet -> Set   ");

        assertEquals("Set", regles.nomAffiche(ImmutableSet.class));
    }

    @Test
    void fichierVide_neDonneAucuneRegle() {
        assertTrue(regles("# rien", "").vides());
        assertTrue(Regles.aucune().vides());
    }

    @Test
    void regleInconnue_estRefuseeEnNommantLaLigne() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> regles("type ImmutableList -> List", "couleur Client -> rouge"));

        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
        assertTrue(e.getMessage().contains("couleur"), e.getMessage());
        assertTrue(e.getMessage().contains("type"), e.getMessage());
    }

    @Test
    void flecheManquante_estRefusee() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> regles("type ImmutableList List"));

        assertTrue(e.getMessage().contains("->"), e.getMessage());
    }

    @Test
    void moitieManquante_estRefusee() {
        assertThrows(IllegalArgumentException.class, () -> regles("type -> List"));
        assertThrows(IllegalArgumentException.class, () -> regles("type ImmutableList ->"));
    }

    @Test
    void deuxReglesSurLeMemeType_sontRefusees() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> regles("type ImmutableList -> List", "type ImmutableList -> Collection"));

        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
        assertTrue(e.getMessage().contains("List"), e.getMessage());
    }

    @Test
    void omitReturnVoid_effaceLeTypeRendu() {
        assertEquals("", regles("omit return void").nomAfficheRetour(void.class));
    }

    @Test
    void omitReturnVoid_neToucheQueLeRetour() {
        Regles regles = regles("omit return void");

        assertEquals("void", regles.nomAffiche(void.class));
    }

    @Test
    void omitReturnVoid_neToucheAucunAutreType() {
        assertEquals("String", regles("omit return void").nomAfficheRetour(String.class));
    }

    @Test
    void sansOmitReturnVoid_leVoidResteEcrit() {
        assertEquals("void", Regles.aucune().nomAfficheRetour(void.class));
    }

    @Test
    void omitReturnVoid_seCombineAvecUnAlias() {
        Regles regles = regles("omit return void", "type ImmutableList -> List");

        assertEquals("", regles.nomAfficheRetour(void.class));
        assertEquals("List", regles.nomAfficheRetour(ImmutableList.class));
    }

    @Test
    void omitDeValeurInconnue_estRefuseEnRappelantLesQuatreFormes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> regles("omit types"));

        assertTrue(e.getMessage().contains("visibility"), e.getMessage());
        assertTrue(e.getMessage().contains("parameter names"), e.getMessage());
        assertTrue(e.getMessage().contains("return void"), e.getMessage());
        assertTrue(e.getMessage().contains("abstract"), e.getMessage());
    }

    @Test
    void omitAbstract_estLu() {
        assertTrue(regles("omit abstract").omettreAbstrait());
    }

    @Test
    void sansOmitAbstract_laMarqueResteDemandee() {
        assertFalse(Regles.aucune().omettreAbstrait());
        assertFalse(regles("omit visibility").omettreAbstrait());
    }

    @Test
    void deuxFoisOmitAbstract_sontRefuses() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("omit abstract", "omit abstract"));

        assertTrue(refus.getMessage().contains("already asked for"), refus.getMessage());
    }

    @Test
    void omitAbstract_neSuffitPasAFaireDesReglesVides() {
        assertFalse(regles("omit abstract").vides());
    }

    @Test
    void deuxFoisLeMemeOmit_sontRefuses() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("omit return void", "omit return void"));

        assertTrue(refus.getMessage().contains("already asked for"), refus.getMessage());
    }

    @Test
    void hideReturn_nExistePlus() {
        assertThrows(IllegalArgumentException.class, () -> regles("omit return String"));
    }

    @Test
    void fichierAbsent_donneUnJeuVide(@TempDir Path tmp) throws IOException {
        assertTrue(Regles.duProjet(tmp).vides(),
            "L'immense majorité des projets n'ont pas de fichier de règles.");
    }

    @Test
    void fichierPresent_estLuALaRacineDuRepertoireAnalyse(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve(Regles.NOM_DU_FICHIER),
            "type ImmutableList -> List\n", StandardCharsets.UTF_8);

        assertEquals("List", Regles.duProjet(tmp).nomAffiche(ImmutableList.class));
    }

    @Test
    void fichierFautif_arreteLaGeneration(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve(Regles.NOM_DU_FICHIER), "type ImmutableList\n",
            StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> Regles.duProjet(tmp));
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Clavier {
        private ImmutableList<String> touches;

        public ImmutableSet<String> noms(ImmutableList<String> filtre) {
            return ImmutableSet.of();
        }
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Interrupteur {
        public void basculer() {}

        public String etat() {
            return "";
        }
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    interface Bascule {
        String etat();

        default boolean allumee() {
            return false;
        }

        static Bascule neuve() {
            return null;
        }
    }

    @Test
    void sansOmitAbstract_lOperationAbstraitePorteSaMarque() {
        String source =
            PlantUmlAssert.documentPour(EntityExtractor.extract(Bascule.class, Regles.aucune()));

        assertTrue(source.contains("{abstract} etat()"),
            "L'italique est la notation d'UML pour une opération sans implémentation :\n"
                + source);
    }

    @Test
    void omitAbstract_effaceLaMarqueEtLaisseLeReste() {
        String source = PlantUmlAssert.documentPour(
            EntityExtractor.extract(Bascule.class, regles("omit abstract")));

        assertFalse(source.contains("{abstract}"), "La marque doit disparaître :\n" + source);

        assertTrue(source.contains("+ etat() : String"), source);
        assertTrue(source.contains("+ allumee() : boolean"), source);
        assertTrue(source.contains("{static} neuve()"), source);
    }

    @Test
    void omitAbstract_neTouchePasLeMotCleDeLaBoite() {
        String source = PlantUmlAssert.documentPour(
            EntityExtractor.extract(Bascule.class, regles("omit abstract")));

        assertTrue(source.contains("interface "), source);
    }

    @Test
    void omitReturnVoid_effaceLeVoidDeLaBoiteEtLuiSeul() {
        String source = PlantUmlAssert.documentPour(EntityExtractor.extract(Interrupteur.class,
            regles("omit return void")));

        assertTrue(source.contains("+ basculer()\n"),
            "Le void ne doit laisser ni type ni deux-points :\n" + source);
        assertTrue(source.contains("+ etat() : String"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void sansRegle_leVoidEstEcrit() {
        String source = PlantUmlAssert.documentPour(EntityExtractor.extract(Interrupteur.class));

        assertTrue(source.contains("+ basculer() : void"), source);
    }

    @Test
    void regle_sappliqueAuxAttributsAuxRetoursEtAuxParametres() {
        Regles regles = regles("type ImmutableList -> List", "type ImmutableSet -> Set");

        List<UmlField> champs =
            MemberExtractor.extractFields(Clavier.class, UMLVisibility.PRIVATE, regles);
        var methode = MemberExtractor.extractMethods(Clavier.class, UMLVisibility.PRIVATE, regles)
            .stream().filter(m -> m.name().equals("noms")).findFirst().orElseThrow();

        assertEquals("List<String>", champs.get(0).type(), "L'attribut : " + champs);
        assertEquals("Set<String>", methode.returnType());
        assertEquals("List<String>", methode.parameters().get(0).type());
    }

    @Test
    void sansRegle_leCodeSeDecritTelQuilEstEcrit() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Clavier.class, UMLVisibility.PRIVATE, Regles.aucune());

        assertEquals("ImmutableList<String>", champs.get(0).type());
    }

    @Test
    void strategieEcrite_seDistingueDeLAbsenceDeLigne() {
        assertTrue(regles("strategy none").strategieDeclaree());
        assertFalse(regles().strategieDeclaree());
    }

    @Test
    void ordreDeDeclaration_seDemande() {
        assertTrue(regles("order declaration").ordreDeclaration());
        assertFalse(regles("order alphabetical").ordreDeclaration());
        assertFalse(regles().ordreDeclaration());
    }

    @Test
    void ordreInconnu_estRefuseEnNommantLesDeux() {
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("order source"));

        assertTrue(refus.getMessage().contains("declaration"), refus.getMessage());
        assertTrue(refus.getMessage().contains("alphabetical"), refus.getMessage());
    }

    @Test
    void deuxLignesOrder_sontRefusees() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("order declaration", "order alphabetical"));

        assertTrue(refus.getMessage().contains("already set"), refus.getMessage());
    }

    @Test
    void nomsDeParametres_seTaisent() {
        assertTrue(regles("omit parameter names").omettreNomsDeParametres());
        assertFalse(regles().omettreNomsDeParametres());
    }

    @Test
    void unMotif_viseLesDeuxSortesDeMembres() {
        Regles lues = regles("hide log, get*");

        assertTrue(lues.membreMasque("log"));
        assertTrue(lues.membreMasque("getNom"));
        assertFalse(lues.membreMasque("calculer"));
    }

    @Test
    void hideMethodEtHideField_neSontPlusDesMotsCles() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("hide method get*"));

        assertTrue(refus.getMessage().contains("cannot contain a space"), refus.getMessage());
    }

    @Test
    void motifQualifie_estRefuseEnRenvoyantVersUmlExclude() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("hide Representation.string"));

        assertTrue(refus.getMessage().contains("@UMLExclude"), refus.getMessage());
        assertTrue(refus.getMessage().contains("Representation.string"), refus.getMessage());
    }

    @Test
    void motifOrdinaire_resteAccepte() {
        assertTrue(regles("hide s*").membreMasque("string"));
    }

    @Test
    void unFichierQuiNestPasEnUtf8_estRefuseEnLeDisant(@TempDir Path tmp) throws IOException {
        Path fichier = tmp.resolve("umlg.rules");
        Files.write(fichier,
            "# règles du projet\nstrategy public\n".getBytes(StandardCharsets.ISO_8859_1));

        var refus = assertThrows(IllegalArgumentException.class, () -> Regles.duFichier(fichier));

        assertTrue(refus.getMessage().contains("UTF-8"), refus.getMessage());
        assertTrue(refus.getMessage().contains(fichier.toString()), refus.getMessage());
    }

    @Test
    void motifQualifieAvecEtoile_estRefuseAussi() {
        assertThrows(IllegalArgumentException.class, () -> regles("hide Representation.*"));
    }
}

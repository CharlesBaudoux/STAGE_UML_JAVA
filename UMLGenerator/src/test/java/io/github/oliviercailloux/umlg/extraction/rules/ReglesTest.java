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

// Le fichier de règles : ce qu'il accepte, ce qu'il refuse, et ce qu'il change au diagramme.
//
// Deux exigences se répondent. Un fichier écrit à la main n'a pas de compilateur pour le relire :
// tout refus doit donc nommer la ligne et rappeler la forme attendue. Et une règle acceptée doit
// se voir sur le dessin, sans quoi l'utilisateur croirait l'avoir appliquée.
class ReglesTest {

    private static Regles regles(String... lignes) {
        return Regles.lues(ImmutableList.copyOf(lignes), "umlg.rules");
    }

    // --- Ce que le fichier dit ---

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
        // Une règle qui désigne précisément une classe doit primer sur celle qui vise toutes ses
        // homonymes : autrement, la précision demandée serait sans effet.
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

    // --- Ce que le fichier refuse ---

    @Test
    void regleInconnue_estRefuseeEnNommantLaLigne() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> regles("type ImmutableList -> List", "couleur Client -> rouge"));

        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
        assertTrue(e.getMessage().contains("couleur"), e.getMessage());
        // Le message doit dire ce qui est connu, sinon l'auteur n'a rien pour se corriger.
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
        // La seconde l'emporterait en silence, et l'auteur ne saurait pas laquelle s'applique.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> regles("type ImmutableList -> List", "type ImmutableList -> Collection"));

        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
        assertTrue(e.getMessage().contains("List"), e.getMessage());
    }

    // --- « omit return void » : le type de retour que l'on tait ---

    @Test
    void omitReturnVoid_effaceLeTypeRendu() {
        // UML tient le type de retour pour facultatif, et l'omettre veut dire « ne rend rien ».
        assertEquals("", regles("omit return void").nomAfficheRetour(void.class));
    }

    @Test
    void omitReturnVoid_neToucheQueLeRetour() {
        Regles regles = regles("omit return void");

        // Le même type demandé pour un attribut ou un paramètre garde son nom : là, il porte ce
        // que la ligne a à dire.
        assertEquals("void", regles.nomAffiche(void.class));
    }

    @Test
    void omitReturnVoid_neToucheAucunAutreType() {
        // La règle ne vaut que pour void : effacer un retour utile — « names(): String » devenant
        // « names() » — ferait perdre au dessin ce que la boîte existe pour montrer.
        assertEquals("String", regles("omit return void").nomAfficheRetour(String.class));
    }

    @Test
    void sansOmitReturnVoid_leVoidResteEcrit() {
        assertEquals("void", Regles.aucune().nomAfficheRetour(void.class));
    }

    @Test
    void omitReturnVoid_seCombineAvecUnAlias() {
        // Les deux règles cohabitent : l'une efface un retour, l'autre renomme un type.
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

    // --- « omit abstract » : la marque d'une opération sans implémentation ---

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
        // « vides » sert à savoir si un fichier a été trouvé : une règle écrite, quelle qu'elle
        // soit, doit l'en faire sortir.
        assertFalse(regles("omit abstract").vides());
    }

    @Test
    void deuxFoisLeMemeOmit_sontRefuses() {
        // La seconde ne demanderait rien de plus ; un fichier qui se répète est un fichier que
        // personne ne relit.
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("omit return void", "omit return void"));

        assertTrue(refus.getMessage().contains("already asked for"), refus.getMessage());
    }

    @Test
    void hideReturn_nExistePlus() {
        // Le retour n'est plus affaire de « hide » : il ne fait disparaître aucun membre.
        assertThrows(IllegalArgumentException.class, () -> regles("omit return String"));
    }

    // --- Le fichier, sur le disque ---

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
        // Il a été écrit pour être appliqué : l'ignorer donnerait un diagramme qui n'est pas
        // celui qu'on a demandé, sans que rien ne le dise.
        Files.writeString(tmp.resolve(Regles.NOM_DU_FICHIER), "type ImmutableList\n",
            StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> Regles.duProjet(tmp));
    }

    // --- Ce que les règles changent dans une boîte ---

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

    // Les trois sortes de méthode qu'une interface peut porter : abstraite, default, static. La
    // marque {abstract} ne doit toucher que la première, avant comme après la règle.
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

        // Ce que la règle ne touche pas : la méthode reste, avec son type de retour, et
        // {static} — qui dit autre chose — survit.
        assertTrue(source.contains("+ etat() : String"), source);
        assertTrue(source.contains("+ allumee() : boolean"), source);
        assertTrue(source.contains("{static} neuve()"), source);
    }

    @Test
    void omitAbstract_neTouchePasLeMotCleDeLaBoite() {
        // « abstract » désigne l'italique d'une opération, pas le mot écrit en tête d'une boîte :
        // une classe abstraite reste annoncée comme telle.
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

        // La règle s'applique aussi à l'intérieur des chevrons : le renommage porte sur chaque
        // nom de classe rencontré, pas seulement sur celui de tête.
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

    // --- La ligne « strategy », lue ou absente ---

    @Test
    void strategieEcrite_seDistingueDeLAbsenceDeLigne() {
        // « strategy none » et l'absence de ligne donnent le même dessin, mais pas le même
        // signalement : une boîte vide sous une ligne écrite est un choix, la même boîte sans
        // ligne est une surprise que la génération doit expliquer.
        assertTrue(regles("strategy none").strategieDeclaree());
        assertFalse(regles().strategieDeclaree());
    }

    // --- « order » : l'ordre des membres ---

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
        // Même quand la seconde redit le défaut : deux lignes qui parlent du même réglage sont
        // le début d'un fichier que personne ne relit.
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("order declaration", "order alphabetical"));

        assertTrue(refus.getMessage().contains("already set"), refus.getMessage());
    }

    // --- « omit parameter names » : les signatures courtes ---

    @Test
    void nomsDeParametres_seTaisent() {
        assertTrue(regles("omit parameter names").omettreNomsDeParametres());
        assertFalse(regles().omettreNomsDeParametres());
    }

    // --- Un motif vise les méthodes et les attributs indifféremment ---

    @Test
    void unMotif_viseLesDeuxSortesDeMembres() {
        // Il n'y a plus de « hide method » ni de « hide field » : un nom appartient presque
        // toujours à l'un ou à l'autre, et la ligne dit ce qu'elle veut dire sans les distinguer.
        Regles lues = regles("hide log, get*");

        assertTrue(lues.membreMasque("log"));
        assertTrue(lues.membreMasque("getNom"));
        assertFalse(lues.membreMasque("calculer"));
    }

    @Test
    void hideMethodEtHideField_neSontPlusDesMotsCles() {
        // Ils passent maintenant pour des motifs séparés par une espace, ce que le message
        // relève en renvoyant vers les familles de bruit et vers « omit ».
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("hide method get*"));

        assertTrue(refus.getMessage().contains("cannot contain a space"), refus.getMessage());
    }

    // --- Aucune règle ne désigne un membre nommément ---

    @Test
    void motifQualifie_estRefuseEnRenvoyantVersUmlExclude() {
        // Une règle ne désigne pas un élément par son nom : c'est le partage même entre le
        // fichier et les annotations, et @UMLExclude est fait pour ce cas-là.
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("hide Representation.string"));

        assertTrue(refus.getMessage().contains("@UMLExclude"), refus.getMessage());
        assertTrue(refus.getMessage().contains("Representation.string"), refus.getMessage());
    }

    @Test
    void motifOrdinaire_resteAccepte() {
        // Le point est le seul caractère refusé : « s* » décrit bien une famille de noms.
        assertTrue(regles("hide s*").membreMasque("string"));
    }

    // --- L'encodage du fichier ---

    @Test
    void unFichierQuiNestPasEnUtf8_estRefuseEnLeDisant(@TempDir Path tmp) throws IOException {
        Path fichier = tmp.resolve("umlg.rules");
        // « # règles » tel que l'enregistrerait un éditeur Windows : l'accent en CP-1252 est un
        // octet isolé, invalide en UTF-8. Le premier accent d'un commentaire suffisait donc à
        // faire échouer la lecture — derrière l'enveloppe générique de la génération, sans que
        // le message dise jamais le mot « encodage ».
        Files.write(fichier,
            "# règles du projet\nstrategy public\n".getBytes(StandardCharsets.ISO_8859_1));

        var refus = assertThrows(IllegalArgumentException.class, () -> Regles.duFichier(fichier));

        assertTrue(refus.getMessage().contains("UTF-8"), refus.getMessage());
        assertTrue(refus.getMessage().contains(fichier.toString()), refus.getMessage());
    }

    @Test
    void motifQualifieAvecEtoile_estRefuseAussi() {
        // « Representation.* » désignait la boîte entière d'une classe nommée : c'est encore une
        // désignation nommément, et le point suffit à la reconnaître.
        assertThrows(IllegalArgumentException.class, () -> regles("hide Representation.*"));
    }
}

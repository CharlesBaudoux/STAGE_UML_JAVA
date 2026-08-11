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
// tout refus doit donc nommer l'endroit fautif et rappeler la forme attendue. Et une règle
// acceptée doit se voir sur le dessin, sans quoi l'utilisateur croirait l'avoir appliquée.
class ReglesTest {

    private static Regles regles(String json) {
        return Regles.lues(json, "umlg.json");
    }

    // --- Ce que le fichier dit ---

    @Test
    void nomSimple_estRemplace() {
        assertEquals("List", regles("""
            { "types": { "ImmutableList": "List" } }
            """).nomAffiche(ImmutableList.class));
    }

    @Test
    void nomComplet_estRemplace() {
        assertEquals("List", regles("""
            { "types": { "com.google.common.collect.ImmutableList": "List" } }
            """).nomAffiche(ImmutableList.class));
    }

    @Test
    void nomComplet_lEmporteSurLeNomSimple() {
        // Une règle qui désigne précisément une classe doit primer sur celle qui vise toutes ses
        // homonymes : autrement, la précision demandée serait sans effet.
        Regles regles = regles("""
            {
              "types": {
                "ImmutableList": "Collection",
                "com.google.common.collect.ImmutableList": "List"
              }
            }
            """);

        assertEquals("List", regles.nomAffiche(ImmutableList.class));
    }

    @Test
    void typeSansRegle_gardeSonNomSimple() {
        assertEquals("String", regles("""
            { "types": { "ImmutableList": "List" } }
            """).nomAffiche(String.class));
    }

    @Test
    void clesSoulignees_sontIgnorees() {
        // JSON n'a pas de commentaires, et un fichier écrit à la main a besoin de dire pourquoi.
        // Une clé qui commence par « _ » n'est pas une règle : c'est là que va la note.
        Regles regles = regles("""
            {
              "_": "Les types de Guava n'apprennent rien au lecteur.",
              "_pourquoi": "Et une seconde note ne gêne pas la première.",
              "types": { "ImmutableSet": "Set" }
            }
            """);

        assertEquals("Set", regles.nomAffiche(ImmutableSet.class));
    }

    @Test
    void fichierVide_neDonneAucuneRegle() {
        // Un fichier créé mais pas encore écrit n'est pas une faute : il donne les mêmes règles
        // qu'un fichier absent, comme l'objet vide qu'on y écrirait.
        assertTrue(regles("").vides());
        assertTrue(regles("   \n").vides());
        assertTrue(regles("{}").vides());
        assertTrue(Regles.aucune().vides());
    }

    // --- Ce que le fichier refuse ---

    @Test
    void regleInconnue_estRefuseeEnRappelantLesConnues() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> regles("""
            {
              "types": { "ImmutableList": "List" },
              "couleur": { "Client": "rouge" }
            }
            """));

        assertTrue(e.getMessage().contains("umlg.json"), e.getMessage());
        assertTrue(e.getMessage().contains("couleur"), e.getMessage());
        // Le message doit dire ce qui est connu, sinon l'auteur n'a rien pour se corriger.
        assertTrue(e.getMessage().contains("types"), e.getMessage());
        assertTrue(e.getMessage().contains("views"), e.getMessage());
    }

    @Test
    void syntaxeFautive_estRefuseeEnNommantLaLigne() {
        // La faute de syntaxe est la seule que l'analyseur situe mieux que nous : il connaît la
        // ligne, là où le chemin JSON n'existe pas encore.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> regles("""
            {
              "strategy": "public",
            }
            """));

        assertTrue(e.getMessage().contains("umlg.json"), e.getMessage());
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
    }

    @Test
    void commentaireDeLAncienFormat_estRefuseEnDisantOuVaUneNote() {
        // Le fichier s'écrivait ligne à ligne, et les notes s'y ouvraient par « # ». Celui qui
        // porte son ancien fichier en JSON doit lire où sa note peut aller.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> regles("""
            # Les types de Guava n'apprennent rien au lecteur.
            { "types": { "ImmutableSet": "Set" } }
            """));

        assertTrue(e.getMessage().contains("JSON"), e.getMessage());
        assertTrue(e.getMessage().contains("_"), e.getMessage());
    }

    @Test
    void objetDeTeteAttendu_etRienDAutre() {
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("""
            [ { "strategy": "public" } ]
            """));

        assertTrue(refus.getMessage().contains("expected a JSON object"), refus.getMessage());
        assertTrue(refus.getMessage().contains("an array"), refus.getMessage());
    }

    @Test
    void cequiSuitLAccoladeFermante_estRefuse() {
        // Une accolade fermée trop tôt laisse le reste du fichier sans effet, et le diagramme
        // n'est alors pas celui qu'on a demandé.
        assertThrows(IllegalArgumentException.class, () -> regles("""
            { "strategy": "public" }
            { "order": "declaration" }
            """));
    }

    @Test
    void valeurDeLaMauvaiseSorte_estRefuseeEnNommantLaCle() {
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("""
            { "hide": "get*" }
            """));

        assertTrue(refus.getMessage().contains("hide"), refus.getMessage());
        assertTrue(refus.getMessage().contains("array"), refus.getMessage());
    }

    @Test
    void typeAfficheQuiNestPasUneChaine_estRefuse() {
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("""
            { "types": { "ImmutableList": 3 } }
            """));

        assertTrue(refus.getMessage().contains("types.ImmutableList"), refus.getMessage());
        assertTrue(refus.getMessage().contains("a number"), refus.getMessage());
    }

    @Test
    void moitieManquante_estRefusee() {
        assertThrows(IllegalArgumentException.class, () -> regles("""
            { "types": { "": "List" } }
            """));
        assertThrows(IllegalArgumentException.class, () -> regles("""
            { "types": { "ImmutableList": "" } }
            """));
    }

    @Test
    void deuxReglesSurLeMemeType_sontRefusees() {
        // La seconde l'emporterait en silence, et l'auteur ne saurait pas laquelle s'applique.
        // C'est l'analyseur qui le relève ici, et pour toutes les clés d'un coup.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> regles("""
            {
              "types": {
                "ImmutableList": "List",
                "ImmutableList": "Collection"
              }
            }
            """));

        assertTrue(e.getMessage().contains("umlg.json"), e.getMessage());
        assertTrue(e.getMessage().contains("ImmutableList"), e.getMessage());
    }

    // --- « return void » : le type de retour que l'on tait ---

    @Test
    void omitReturnVoid_effaceLeTypeRendu() {
        // UML tient le type de retour pour facultatif, et l'omettre veut dire « ne rend rien ».
        assertEquals("", regles("""
            { "omit": ["return void"] }
            """).nomAfficheRetour(void.class));
    }

    @Test
    void omitReturnVoid_neToucheQueLeRetour() {
        Regles regles = regles("""
            { "omit": ["return void"] }
            """);

        // Le même type demandé pour un attribut ou un paramètre garde son nom : là, il porte ce
        // que l'entrée a à dire.
        assertEquals("void", regles.nomAffiche(void.class));
    }

    @Test
    void omitReturnVoid_neToucheAucunAutreType() {
        // La règle ne vaut que pour void : effacer un retour utile — « names(): String » devenant
        // « names() » — ferait perdre au dessin ce que la boîte existe pour montrer.
        assertEquals("String", regles("""
            { "omit": ["return void"] }
            """).nomAfficheRetour(String.class));
    }

    @Test
    void sansOmitReturnVoid_leVoidResteEcrit() {
        assertEquals("void", Regles.aucune().nomAfficheRetour(void.class));
    }

    @Test
    void omitReturnVoid_seCombineAvecUnAlias() {
        // Les deux règles cohabitent : l'une efface un retour, l'autre renomme un type.
        Regles regles = regles("""
            {
              "omit": ["return void"],
              "types": { "ImmutableList": "List" }
            }
            """);

        assertEquals("", regles.nomAfficheRetour(void.class));
        assertEquals("List", regles.nomAfficheRetour(ImmutableList.class));
    }

    @Test
    void omitDeValeurInconnue_estRefuseEnRappelantLesQuatreFormes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> regles("""
            { "omit": ["types"] }
            """));

        assertTrue(e.getMessage().contains("visibility"), e.getMessage());
        assertTrue(e.getMessage().contains("parameter names"), e.getMessage());
        assertTrue(e.getMessage().contains("return void"), e.getMessage());
        assertTrue(e.getMessage().contains("abstract"), e.getMessage());
        // Et il nomme l'entrée fautive du tableau, non le tableau entier.
        assertTrue(e.getMessage().contains("omit[0]"), e.getMessage());
    }

    // --- « abstract » : la marque d'une opération sans implémentation ---

    @Test
    void omitAbstract_estLu() {
        assertTrue(regles("""
            { "omit": ["abstract"] }
            """).omettreAbstrait());
    }

    @Test
    void sansOmitAbstract_laMarqueResteDemandee() {
        assertFalse(Regles.aucune().omettreAbstrait());
        assertFalse(regles("""
            { "omit": ["visibility"] }
            """).omettreAbstrait());
    }

    @Test
    void deuxFoisOmitAbstract_sontRefuses() {
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("""
            { "omit": ["abstract", "abstract"] }
            """));

        assertTrue(refus.getMessage().contains("already asked for"), refus.getMessage());
    }

    @Test
    void omitAbstract_neSuffitPasAFaireDesReglesVides() {
        // « vides » sert à savoir si un fichier a été trouvé : une règle écrite, quelle qu'elle
        // soit, doit l'en faire sortir.
        assertFalse(regles("""
            { "omit": ["abstract"] }
            """).vides());
    }

    @Test
    void deuxFoisLeMemeOmit_sontRefuses() {
        // La seconde ne demanderait rien de plus ; un fichier qui se répète est un fichier que
        // personne ne relit.
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("""
            { "omit": ["return void", "return void"] }
            """));

        assertTrue(refus.getMessage().contains("already asked for"), refus.getMessage());
    }

    @Test
    void hideReturn_nExistePlus() {
        // Le retour n'est plus affaire de « hide » : il ne fait disparaître aucun membre.
        assertThrows(IllegalArgumentException.class, () -> regles("""
            { "omit": ["return String"] }
            """));
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
            "{ \"types\": { \"ImmutableList\": \"List\" } }\n", StandardCharsets.UTF_8);

        assertEquals("List", Regles.duProjet(tmp).nomAffiche(ImmutableList.class));
    }

    @Test
    void fichierFautif_arreteLaGeneration(@TempDir Path tmp) throws IOException {
        // Il a été écrit pour être appliqué : l'ignorer donnerait un diagramme qui n'est pas
        // celui qu'on a demandé, sans que rien ne le dise.
        Files.writeString(tmp.resolve(Regles.NOM_DU_FICHIER), "{ \"types\": }\n",
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
            EntityExtractor.extract(Bascule.class, regles("""
                { "omit": ["abstract"] }
                """)));

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
            EntityExtractor.extract(Bascule.class, regles("""
                { "omit": ["abstract"] }
                """)));

        assertTrue(source.contains("interface "), source);
    }

    @Test
    void omitReturnVoid_effaceLeVoidDeLaBoiteEtLuiSeul() {
        String source = PlantUmlAssert.documentPour(EntityExtractor.extract(Interrupteur.class,
            regles("""
                { "omit": ["return void"] }
                """)));

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
        Regles regles = regles("""
            { "types": { "ImmutableList": "List", "ImmutableSet": "Set" } }
            """);

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

    // --- La clé « strategy », lue ou absente ---

    @Test
    void strategieEcrite_seDistingueDeLAbsenceDeCle() {
        // « "strategy": "none" » et l'absence de clé donnent le même dessin, mais pas le même
        // signalement : une boîte vide sous une clé écrite est un choix, la même boîte sans clé
        // est une surprise que la génération doit expliquer.
        assertTrue(regles("""
            { "strategy": "none" }
            """).strategieDeclaree());
        assertFalse(regles("{}").strategieDeclaree());
    }

    // --- « order » : l'ordre des membres ---

    @Test
    void ordreDeDeclaration_seDemande() {
        assertTrue(regles("""
            { "order": "declaration" }
            """).ordreDeclaration());
        assertFalse(regles("""
            { "order": "alphabetical" }
            """).ordreDeclaration());
        assertFalse(regles("{}").ordreDeclaration());
    }

    @Test
    void ordreInconnu_estRefuseEnNommantLesDeux() {
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("""
            { "order": "source" }
            """));

        assertTrue(refus.getMessage().contains("declaration"), refus.getMessage());
        assertTrue(refus.getMessage().contains("alphabetical"), refus.getMessage());
    }

    @Test
    void deuxClesOrder_sontRefusees() {
        // Même quand la seconde redit le défaut : deux entrées qui parlent du même réglage sont
        // le début d'un fichier que personne ne relit. C'est l'analyseur qui le dit, à présent.
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("""
            {
              "order": "declaration",
              "order": "alphabetical"
            }
            """));

        assertTrue(refus.getMessage().contains("umlg.json"), refus.getMessage());
        assertTrue(refus.getMessage().contains("order"), refus.getMessage());
    }

    // --- « parameter names » : les signatures courtes ---

    @Test
    void nomsDeParametres_seTaisent() {
        assertTrue(regles("""
            { "omit": ["parameter names"] }
            """).omettreNomsDeParametres());
        assertFalse(regles("{}").omettreNomsDeParametres());
    }

    // --- Un motif vise les méthodes et les attributs indifféremment ---

    @Test
    void unMotif_viseLesDeuxSortesDeMembres() {
        // Il n'y a pas de « hideMethods » ni de « hideFields » : un nom appartient presque
        // toujours à l'un ou à l'autre, et l'entrée dit ce qu'elle veut dire sans les distinguer.
        Regles lues = regles("""
            { "hide": ["log", "get*"] }
            """);

        assertTrue(lues.membreMasque("log"));
        assertTrue(lues.membreMasque("getNom"));
        assertFalse(lues.membreMasque("calculer"));
    }

    @Test
    void deuxMotifsDansLaMemeChaine_sontRefuses() {
        // Le tableau attend un motif par entrée ; deux noms dans la même chaîne ne masqueraient
        // ni l'un ni l'autre, et le message renvoie aussi vers « omit ».
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("""
            { "hide": ["method get*"] }
            """));

        assertTrue(refus.getMessage().contains("cannot contain a space"), refus.getMessage());
        assertTrue(refus.getMessage().contains("hide[0]"), refus.getMessage());
    }

    // --- Aucune règle ne désigne un membre nommément ---

    @Test
    void motifQualifie_estRefuseEnRenvoyantVersUmlExclude() {
        // Une règle ne désigne pas un élément par son nom : c'est le partage même entre le
        // fichier et les annotations, et @UMLExclude est fait pour ce cas-là.
        var refus = assertThrows(IllegalArgumentException.class, () -> regles("""
            { "hide": ["Representation.string"] }
            """));

        assertTrue(refus.getMessage().contains("@UMLExclude"), refus.getMessage());
        assertTrue(refus.getMessage().contains("Representation.string"), refus.getMessage());
    }

    @Test
    void motifOrdinaire_resteAccepte() {
        // Le point est le seul caractère refusé : « s* » décrit bien une famille de noms.
        assertTrue(regles("""
            { "hide": ["s*"] }
            """).membreMasque("string"));
    }

    // --- L'encodage du fichier ---

    @Test
    void unFichierQuiNestPasEnUtf8_estRefuseEnLeDisant(@TempDir Path tmp) throws IOException {
        Path fichier = tmp.resolve("umlg.json");
        // Une note accentuée telle que l'enregistrerait un éditeur Windows : l'accent en CP-1252
        // est un octet isolé, invalide en UTF-8 — que JSON, lui, exige. Le premier accent suffit
        // donc à faire échouer la lecture, derrière l'enveloppe générique de la génération et sans
        // que le message dise jamais le mot « encodage ».
        Files.write(fichier, ("{ \"_\": \"les règles du projet\", \"strategy\": \"public\" }\n")
            .getBytes(StandardCharsets.ISO_8859_1));

        var refus = assertThrows(IllegalArgumentException.class, () -> Regles.duFichier(fichier));

        assertTrue(refus.getMessage().contains("UTF-8"), refus.getMessage());
        assertTrue(refus.getMessage().contains(fichier.toString()), refus.getMessage());
    }

    @Test
    void motifQualifieAvecEtoile_estRefuseAussi() {
        // « Representation.* » désignait la boîte entière d'une classe nommée : c'est encore une
        // désignation nommément, et le point suffit à la reconnaître.
        assertThrows(IllegalArgumentException.class, () -> regles("""
            { "hide": ["Representation.*"] }
            """));
    }
}

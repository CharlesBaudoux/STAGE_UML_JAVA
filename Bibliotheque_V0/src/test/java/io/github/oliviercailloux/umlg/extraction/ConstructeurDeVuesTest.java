package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.model.UmlInheritanceType;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import io.github.oliviercailloux.umlg.model.UmlView;
import java.util.List;
import org.junit.jupiter.api.Test;

// Le découpage du projet en vues : ce que chacune retient, et ce qu'elle fait des traits qui en
// sortent.
class ConstructeurDeVuesTest {

    private static final String PAQ = "com.exemple";
    private static final String AUTRE = "com.autre";

    private static UmlEntity boite(String paquetage, String nom, String... cibles) {
        ImmutableList.Builder<UmlAssociation> liens = ImmutableList.builder();
        for (String cible : cibles) {
            liens.add(UmlAssociation.builder("champ", cible).build());
        }
        return UmlEntity.builder(paquetage + "." + nom, nom).associations(liens.build()).build();
    }

    private static ImmutableSet<UmlEntity> projet() {
        // Facture -> Client, Client -> Adresse, et Journal isolé dans un autre paquetage.
        return ImmutableSet.of(
            boite(PAQ, "Facture", PAQ + ".Client"),
            boite(PAQ, "Client", PAQ + ".Adresse"),
            boite(PAQ, "Adresse"),
            boite(AUTRE, "Journal"));
    }

    private static ImmutableList<UmlView> vues(String... lignes) {
        return ConstructeurDeVues.construire(projet(), UmlDiagram.defaut(),
            Regles.lues(ImmutableList.copyOf(lignes), "les règles du test"));
    }

    private static List<String> noms(UmlView vue) {
        return vue.entities().stream().map(UmlEntity::name).toList();
    }

    private static UmlView vue(ImmutableList<UmlView> toutes, String nom) {
        return toutes.stream().filter(v -> v.name().equals(nom)).findFirst().orElseThrow();
    }

    // --- La vue intégrale ---

    @Test
    void sansAucuneRegle_seuleLIntegraleEstProduite() {
        ImmutableList<UmlView> toutes = vues();

        assertEquals(1, toutes.size(), toutes.toString());
        assertEquals(ConstructeurDeVues.VUE_INTEGRALE, toutes.get(0).name());
        assertEquals(4, toutes.get(0).entities().size());
    }

    @Test
    void lIntegrale_vientToujoursEnPremier() {
        // PlantUmlWriter compte sur cet ordre : la première vue va à la racine du dossier, les
        // autres dans le sous-dossier.
        assertEquals(ConstructeurDeVues.VUE_INTEGRALE,
            vues("view \"Facturation\" = Facture").get(0).name());
    }

    @Test
    void lIntegrale_prendLeTitreDeUmlDiagram() {
        ImmutableList<UmlView> toutes = ConstructeurDeVues.construire(projet(),
            new UmlDiagram("Mon architecture", UMLFlow.LEFT_TO_RIGHT, true), Regles.aucune());

        assertEquals("Mon architecture", toutes.get(0).name());
        assertEquals(UMLFlow.LEFT_TO_RIGHT, toutes.get(0).direction());
    }

    // --- Les vues déclarées ---

    @Test
    void vueDeclaree_neRetientQueCeQuElleNomme() {
        assertEquals(List.of("Facture", "Client"),
            noms(vue(vues("view \"Facturation\" = Facture, Client"), "Facturation")));
    }

    @Test
    void vueParPaquetage_retientToutLePaquetage() {
        assertEquals(List.of("Facture", "Client", "Adresse"),
            noms(vue(vues("view \"Domaine\" = package " + PAQ), "Domaine")));
    }

    @Test
    void vue_peutRetirerCeQuElleARetenu() {
        assertEquals(List.of("Facture", "Adresse"),
            noms(vue(vues("view \"Domaine\" = package " + PAQ + " - Client"), "Domaine")));
    }

    @Test
    void vue_conserveLOrdreDEcriture() {
        // L'ordre écrit est celui de l'exposé qui accompagne le dessin : il doit être tenu.
        assertEquals(List.of("Adresse", "Facture", "Client"),
            noms(vue(vues("view \"À l'envers\" = Adresse, Facture, Client"), "À l'envers")));
    }

    @Test
    void vue_accepteLeNomComplet() {
        assertEquals(List.of("Journal"),
            noms(vue(vues("view \"Ailleurs\" = " + AUTRE + ".Journal"), "Ailleurs")));
    }

    @Test
    void vue_porteSonPropreSensDeLecture() {
        ImmutableList<UmlView> toutes = vues(
            "view \"Facturation\" = Facture, Client",
            "view \"Facturation\" direction left-to-right");

        assertEquals(UMLFlow.LEFT_TO_RIGHT, vue(toutes, "Facturation").direction());
        assertEquals(UMLFlow.TOP_TO_BOTTOM, toutes.get(0).direction(),
            "L'intégrale garde le sien.");
    }

    @Test
    void nomIntrouvable_estRefuse() {
        // Le fichier n'a pas de compilateur : une classe renommée y laisserait un nom mort, et le
        // diagramme se dessinerait amputé sans que personne ne le sache.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> vues("view \"Faute\" = Factuer"));

        assertTrue(e.getMessage().contains("matches no class"), e.getMessage());
    }

    @Test
    void vueEntierementRetiree_estRefusee() {
        assertThrows(IllegalArgumentException.class,
            () -> vues("view \"Vide\" = Facture - Facture"));
    }

    // --- Le voisinage ---

    @Test
    void voisinage_prendLaClasseEtCeQuiLaTouche() {
        // Client vise Adresse, et Facture vise Client : les trois, et pas Journal.
        assertEquals(List.of("Facture", "Client", "Adresse"),
            noms(vue(vues("view \"Autour de Client\" = around Client"), "Autour de Client")));
    }

    @Test
    void voisinage_neVaPasAuDela() {
        // Autour d'Adresse : Client la vise, mais Facture est à deux pas et reste dehors.
        assertEquals(List.of("Client", "Adresse"),
            noms(vue(vues("view \"Autour d'Adresse\" = around Adresse"), "Autour d'Adresse")));
    }

    // --- Les vues calculées par paquetage ---

    @Test
    void viewsByPackage_donneUneVueParPaquetage() {
        ImmutableList<UmlView> toutes = vues("views by package");

        assertEquals(3, toutes.size(), toutes.toString());
        assertEquals(List.of("Journal"), noms(vue(toutes, AUTRE)));
        assertEquals(3, vue(toutes, PAQ).entities().size());
    }

    // --- L'élagage des traits sortants ---

    @Test
    void unLienVersUneBoiteAbsente_estRetire() {
        // Facture vise Client, qui n'est pas dans la vue : le trait ne peut pas être dessiné, et
        // le laisser ferait échouer le rendu sur un alias inconnu.
        UmlView seule = vue(vues("view \"Seule\" = Facture"), "Seule");

        assertTrue(seule.entities().get(0).associations().isEmpty(),
            seule.entities().get(0).relations().toString());
    }

    @Test
    void unLienInterneALaVue_estConserve() {
        UmlView deux = vue(vues("view \"Deux\" = Facture, Client"), "Deux");

        assertEquals(1, deux.entities().get(0).associations().size());
    }

    @Test
    void lIntegrale_neRetireAucunLien() {
        assertEquals(1, vues().get(0).entities().iterator().next().associations().size());
    }

    // --- L'ordre imposé par des liens invisibles ---

    @Test
    void deuxBoitesQueRienNeRelie_sontEnchainees() {
        // Le moteur les poserait côte à côte, au hasard. L'ordre écrit est celui de l'exposé.
        UmlView ordonnee = vue(vues("view \"Ordre\" = Adresse, Journal"), "Ordre");

        ImmutableList<UmlLayout> liens = ordonnee.entities().get(0).layouts();
        assertEquals(1, liens.size(), ordonnee.entities().get(0).relations().toString());
        assertEquals(AUTRE + ".Journal", liens.get(0).targetQualifiedName());
    }

    @Test
    void deuxBoitesDejaReliees_neSontPasEnchainees() {
        // Un trait existe déjà entre elles : en ajouter un invisible contraindrait deux fois la
        // même paire, et figerait un dessin que le moteur compose mieux que nous.
        UmlView deux = vue(vues("view \"Deux\" = Facture, Client"), "Deux");

        assertTrue(deux.entities().get(0).layouts().isEmpty(),
            deux.entities().get(0).relations().toString());
    }

    // Une famille — un parent, trois filles — puis deux boîtes seules : la forme exacte du
    // diagramme « Mapping » écrit à la main par le professeur.
    private static ImmutableSet<UmlEntity> famille() {
        return ImmutableSet.of(
            UmlEntity.builder(PAQ + ".KeysymEntry", "KeysymEntry").build(),
            fille("Mnemonic"), fille("Ucp"), fille("Code"),
            boite(PAQ, "KeyboardMap"),
            boite(PAQ, "XkbSymbolsReader"));
    }

    private static UmlEntity fille(String nom) {
        return UmlEntity.builder(PAQ + "." + nom, nom)
            .relations(List.of(new UmlInheritance(PAQ + ".KeysymEntry",
                UmlInheritanceType.GENERALIZATION)))
            .build();
    }

    // Les liens invisibles de la vue, sous la forme « source -> cible », en noms simples.
    private static List<String> enchainements(UmlView vue) {
        return vue.entities().stream()
            .flatMap(e -> e.layouts().stream()
                .map(l -> e.name() + " -> "
                    + l.targetQualifiedName().substring(l.targetQualifiedName().
                        lastIndexOf('.') + 1)))
            .toList();
    }

    @Test
    void desSoeurs_neSontPasEmpileesLesUnesSousLesAutres() {
        // Le piège que ce test protège : Mnemonic, Ucp et Code ne sont pas reliées entre elles,
        // mais elles appartiennent au même bloc — le moteur les dispose en éventail sous leur
        // parent, et les enchaîner casserait la forme même de l'héritage.
        UmlView vue = ConstructeurDeVues.construire(famille(), UmlDiagram.defaut(),
            Regles.lues(ImmutableList.of("view \"Mapping\" = KeysymEntry, Mnemonic, Ucp, Code, "
                + "KeyboardMap, XkbSymbolsReader"), "test")).get(1);

        assertEquals(List.of("KeysymEntry -> KeyboardMap", "KeyboardMap -> XkbSymbolsReader"),
            enchainements(vue),
            "Un lien par saut de bloc, et rien à l'intérieur d'un bloc.");
    }

    @Test
    void uneVueDontToutSeTient_nEstPasContrainte() {
        UmlView vue = ConstructeurDeVues.construire(famille(), UmlDiagram.defaut(),
            Regles.lues(ImmutableList.of("view \"Famille\" = KeysymEntry, Mnemonic, Ucp, Code"),
                "test")).get(1);

        assertTrue(enchainements(vue).isEmpty(),
            "Le moteur a tout ce qu'il lui faut : " + enchainements(vue));
    }

    @Test
    void leRepresentantDUnBloc_estSaPremiereBoiteEcrite() {
        // L'ordre d'écriture décide aussi de quelle boîte porte le lien : c'est celle que
        // l'utilisateur a nommée en premier dans son bloc.
        UmlView vue = ConstructeurDeVues.construire(famille(), UmlDiagram.defaut(),
            Regles.lues(ImmutableList.of(
                "view \"Mapping\" = Mnemonic, KeysymEntry, Ucp, Code, KeyboardMap"), "test"))
            .get(1);

        assertEquals(List.of("Mnemonic -> KeyboardMap"), enchainements(vue));
    }

    @Test
    void laDerniereBoite_neTireVersRien() {
        UmlView ordonnee = vue(vues("view \"Ordre\" = Adresse, Journal"), "Ordre");

        assertTrue(ordonnee.entities().get(1).layouts().isEmpty());
    }

    // --- Le nom de fichier ---

    @Test
    void leNomDeFichier_estNettoye() {
        assertEquals("autour-d-adresse",
            new UmlView("Autour d'Adresse", ImmutableList.copyOf(projet()),
                UMLFlow.TOP_TO_BOTTOM).nomDeFichier());
        assertEquals("modele-de-facturation",
            new UmlView("Modèle de facturation !", ImmutableList.copyOf(projet()),
                UMLFlow.TOP_TO_BOTTOM).nomDeFichier());
    }

    @Test
    void projetVide_neDonneAucuneVue() {
        // C'est UmlProject qui refuse un projet sans classe, avec un message utile ; échouer
        // avant lui mettrait l'utilisateur sur une fausse piste.
        assertTrue(ConstructeurDeVues.construire(ImmutableSet.of(), UmlDiagram.defaut(),
            Regles.aucune()).isEmpty());
    }

    @Test
    void uneVueNeChangeNiLesBoitesNiLeursMembres() {
        UmlEntity dansLIntegrale = vues().get(0).entities().iterator().next();
        UmlEntity dansLaVue =
            vue(vues("view \"Deux\" = Facture, Client"), "Deux").entities().get(0);

        assertEquals(dansLIntegrale.name(), dansLaVue.name());
        assertEquals(dansLIntegrale.fields(), dansLaVue.fields());
        assertFalse(dansLaVue.relations().isEmpty());
    }

    // --- Les boîtes déclarées et les vues par paquetage ---

    @Test
    void uneBoiteDeclaree_nEntreDansAucuneVueParPaquetage() {
        // Une boîte déclarée n'a pas de paquetage : son nom qualifié est son nom simple. La
        // ranger dans un paquetage « (défaut) » fabriquait une vue entière que personne n'avait
        // demandée — deux boîtes orphelines, sans leur parent ni la flèche d'héritage, sortis
        // de la vue avec lui.
        UmlEntity declaree = UmlEntity.builder("StringRepresentation", "StringRepresentation")
            .declared(true).build();
        ImmutableSet<UmlEntity> entites = ImmutableSet.<UmlEntity>builder()
            .addAll(projet()).add(declaree).build();

        ImmutableList<UmlView> toutes = ConstructeurDeVues.construire(entites,
            UmlDiagram.defaut(),
            Regles.lues(ImmutableList.of("views by package"), "les règles du test"));

        assertTrue(toutes.stream().noneMatch(v -> v.name().equals("(défaut)")),
            toutes.stream().map(UmlView::name).toList().toString());
        // La boîte n'est pas perdue pour autant : la vue intégrale la montre, avec sa marque.
        assertTrue(noms(toutes.get(0)).contains("StringRepresentation"));
    }
}

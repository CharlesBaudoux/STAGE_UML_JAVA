package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        return ImmutableSet.of(
            boite(PAQ, "Facture", PAQ + ".Client"),
            boite(PAQ, "Client", PAQ + ".Adresse"),
            boite(PAQ, "Adresse"),
            boite(AUTRE, "Journal"));
    }

    private static ImmutableList<UmlView> vues(String json) {
        return ConstructeurDeVues.construire(projet(), UmlDiagram.defaut(),
            Regles.lues(json, "les règles du test"));
    }

    private static ImmutableList<UmlView> uneVue(String vue) {
        return vues("{ \"views\": [" + vue + "] }");
    }

    private static List<String> noms(UmlView vue) {
        return vue.entities().stream().map(UmlEntity::name).toList();
    }

    private static UmlView vue(ImmutableList<UmlView> toutes, String nom) {
        return toutes.stream().filter(v -> v.name().equals(nom)).findFirst().orElseThrow();
    }


    @Test
    void sansAucuneRegle_seuleLIntegraleEstProduite() {
        ImmutableList<UmlView> toutes = vues("{}");

        assertEquals(1, toutes.size(), toutes.toString());
        assertEquals(ConstructeurDeVues.VUE_INTEGRALE, toutes.get(0).name());
        assertEquals(4, toutes.get(0).entities().size());
    }

    @Test
    void lIntegrale_vientToujoursEnPremier() {
        assertEquals(ConstructeurDeVues.VUE_INTEGRALE,
            uneVue("""
                { "name": "Facturation", "retain": ["Facture"] }
                """)
                    .get(0).name());
    }

    @Test
    void lIntegrale_prendLeTitreDeUmlDiagram() {
        ImmutableList<UmlView> toutes = ConstructeurDeVues.construire(projet(),
            new UmlDiagram("Mon architecture", UMLFlow.LEFT_TO_RIGHT, true), Regles.aucune());

        assertEquals("Mon architecture", toutes.get(0).name());
        assertEquals(UMLFlow.LEFT_TO_RIGHT, toutes.get(0).direction());
    }


    @Test
    void vueDeclaree_neRetientQueCeQuElleNomme() {
        assertEquals(List.of("Facture", "Client"),
            noms(vue(uneVue("""
                { "name": "Facturation", "retain": ["Facture", "Client"] }
                """), "Facturation")));
    }

    @Test
    void vueParPaquetage_retientToutLePaquetage() {
        assertEquals(List.of("Facture", "Client", "Adresse"),
            noms(vue(uneVue("""
                { "name": "Domaine", "retain": [{ "package": "%s" }] }
                """.formatted(PAQ)), "Domaine")));
    }

    @Test
    void vue_peutRetirerCeQuElleARetenu() {
        assertEquals(List.of("Facture", "Adresse"),
            noms(vue(uneVue("""
                {
                  "name": "Domaine",
                  "retain": [{ "package": "%s" }],
                  "remove": ["Client"]
                }
                """.formatted(PAQ)), "Domaine")));
    }

    @Test
    void vue_conserveLOrdreDEcriture() {
        assertEquals(List.of("Adresse", "Facture", "Client"),
            noms(vue(uneVue("""
                { "name": "À l'envers", "retain": ["Adresse", "Facture", "Client"] }
                """), "À l'envers")));
    }

    @Test
    void vue_accepteLeNomComplet() {
        assertEquals(List.of("Journal"),
            noms(vue(uneVue("""
                { "name": "Ailleurs", "retain": ["%s.Journal"] }
                """.formatted(AUTRE)), "Ailleurs")));
    }

    @Test
    void vue_porteSonPropreSensDeLecture() {
        ImmutableList<UmlView> toutes = uneVue("""
            {
              "name": "Facturation",
              "retain": ["Facture", "Client"],
              "direction": "left-to-right"
            }
            """);

        assertEquals(UMLFlow.LEFT_TO_RIGHT, vue(toutes, "Facturation").direction());
        assertEquals(UMLFlow.TOP_TO_BOTTOM, toutes.get(0).direction(),
            "L'intégrale garde le sien.");
    }

    @Test
    void nomIntrouvable_estRefuse() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> uneVue("""
                { "name": "Faute", "retain": ["Factuer"] }
                """));

        assertTrue(e.getMessage().contains("matches no class"), e.getMessage());
    }

    @Test
    void vueEntierementRetiree_estRefusee() {
        assertThrows(IllegalArgumentException.class,
            () -> uneVue("""
                { "name": "Vide", "retain": ["Facture"], "remove": ["Facture"] }
                """));
    }


    @Test
    void voisinage_prendLaClasseEtCeQuiLaTouche() {
        assertEquals(List.of("Facture", "Client", "Adresse"),
            noms(vue(uneVue("""
                { "name": "Autour de Client", "retain": [{ "around": "Client" }] }
                """), "Autour de Client")));
    }

    @Test
    void voisinage_neVaPasAuDela() {
        assertEquals(List.of("Client", "Adresse"),
            noms(vue(uneVue("""
                { "name": "Autour d'Adresse", "retain": [{ "around": "Adresse" }] }
                """), "Autour d'Adresse")));
    }


    @Test
    void viewsByPackage_donneUneVueParPaquetage() {
        ImmutableList<UmlView> toutes = vues("""
            { "viewsByPackage": true }
            """);

        assertEquals(3, toutes.size(), toutes.toString());
        assertEquals(List.of("Journal"), noms(vue(toutes, AUTRE)));
        assertEquals(3, vue(toutes, PAQ).entities().size());
    }


    @Test
    void unLienVersUneBoiteAbsente_estRetire() {
        UmlView seule = vue(uneVue("""
            { "name": "Seule", "retain": ["Facture"] }
            """), "Seule");

        assertTrue(seule.entities().get(0).associations().isEmpty(),
            seule.entities().get(0).relations().toString());
    }

    @Test
    void unLienInterneALaVue_estConserve() {
        UmlView deux = vue(uneVue("""
            { "name": "Deux", "retain": ["Facture", "Client"] }
            """), "Deux");

        assertEquals(1, deux.entities().get(0).associations().size());
    }

    @Test
    void uneBoiteDontAucunLienNeSort_nEstPasRecopiee() {
        ImmutableSet<UmlEntity> projet = projet();
        UmlEntity adresse = projet.stream().filter(e -> e.name().equals("Adresse"))
            .findFirst().orElseThrow();

        UmlView tout = ConstructeurDeVues.construire(projet, UmlDiagram.defaut(),
            Regles.lues("""
                { "views": [ { "name": "Tout", "retain": [{ "package": "%s" }] } ] }
                """.formatted(PAQ), "test")).get(1);

        assertSame(adresse, tout.entities().get(2));
    }

    @Test
    void lIntegrale_neRetireAucunLien() {
        assertEquals(1, vues("{}").get(0).entities().iterator().next().associations().size());
    }


    @Test
    void deuxBoitesQueRienNeRelie_sontEnchainees() {
        UmlView ordonnee = vue(uneVue("""
            { "name": "Ordre", "retain": ["Adresse", "Journal"] }
            """), "Ordre");

        ImmutableList<UmlLayout> liens = ordonnee.entities().get(0).layouts();
        assertEquals(1, liens.size(), ordonnee.entities().get(0).relations().toString());
        assertEquals(AUTRE + ".Journal", liens.get(0).targetQualifiedName());
    }

    @Test
    void deuxBoitesDejaReliees_neSontPasEnchainees() {
        UmlView deux = vue(uneVue("""
            { "name": "Deux", "retain": ["Facture", "Client"] }
            """), "Deux");

        assertTrue(deux.entities().get(0).layouts().isEmpty(),
            deux.entities().get(0).relations().toString());
    }

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
        UmlView vue = ConstructeurDeVues.construire(famille(), UmlDiagram.defaut(),
            Regles.lues("""
                { "views": [ { "name": "Mapping", "retain": ["KeysymEntry", "Mnemonic",
                    "Ucp", "Code", "KeyboardMap", "XkbSymbolsReader"] } ] }
                """, "test")).get(1);

        assertEquals(List.of("KeysymEntry -> KeyboardMap", "KeyboardMap -> XkbSymbolsReader"),
            enchainements(vue),
            "Un lien par saut de bloc, et rien à l'intérieur d'un bloc.");
    }

    @Test
    void uneVueDontToutSeTient_nEstPasContrainte() {
        UmlView vue = ConstructeurDeVues.construire(famille(), UmlDiagram.defaut(),
            Regles.lues("""
                { "views": [ { "name": "Famille",
                    "retain": ["KeysymEntry", "Mnemonic", "Ucp", "Code"] } ] }
                """, "test")).get(1);

        assertTrue(enchainements(vue).isEmpty(),
            "Le moteur a tout ce qu'il lui faut : " + enchainements(vue));
    }

    @Test
    void leRepresentantDUnBloc_estSaPremiereBoiteEcrite() {
        UmlView vue = ConstructeurDeVues.construire(famille(), UmlDiagram.defaut(),
            Regles.lues("""
                { "views": [ { "name": "Mapping", "retain": ["Mnemonic", "KeysymEntry",
                    "Ucp", "Code", "KeyboardMap"] } ] }
                """, "test")).get(1);

        assertEquals(List.of("Mnemonic -> KeyboardMap"), enchainements(vue));
    }

    @Test
    void laDerniereBoite_neTireVersRien() {
        UmlView ordonnee = vue(uneVue("""
            { "name": "Ordre", "retain": ["Adresse", "Journal"] }
            """), "Ordre");

        assertTrue(ordonnee.entities().get(1).layouts().isEmpty());
    }


    @Test
    void deuxVuesQuiSeRamenentAuMemeFichier_sontRefusees() {
        IllegalArgumentException refus =
            assertThrows(IllegalArgumentException.class, () -> vues("""
                {
                  "views": [
                    { "name": "Modèle", "retain": ["Facture"] },
                    { "name": "Modele", "retain": ["Client"] }
                  ]
                }
                """));

        assertTrue(refus.getMessage().contains("\"Modèle\""), refus.getMessage());
        assertTrue(refus.getMessage().contains("\"Modele\""), refus.getMessage());
        assertTrue(refus.getMessage().contains("\"modele\""), refus.getMessage());
    }

    @Test
    void deuxVuesQuiNeDifferentQueParLaPonctuation_sontRefusees() {
        assertThrows(IllegalArgumentException.class, () -> vues("""
            {
              "views": [
                { "name": "Le mapping", "retain": ["Facture"] },
                { "name": "Le-Mapping", "retain": ["Client"] }
              ]
            }
            """));
    }

    @Test
    void uneVueDeclareeEtUneVueDePaquetage_neSeMarchentPasDessusEnSilence() {
        assertThrows(IllegalArgumentException.class, () -> vues("""
            {
              "viewsByPackage": true,
              "views": [ { "name": "com, autre", "retain": ["Journal"] } ]
            }
            """));
    }

    @Test
    void desVuesDeNomsBienDistincts_sontToutesProduites() {
        assertEquals(3, vues("""
            {
              "views": [
                { "name": "Modèle", "retain": ["Facture"] },
                { "name": "Journal", "retain": ["Journal"] }
              ]
            }
            """).size());
    }

    @Test
    void uneVueQuiPorteLeNomDeLIntegrale_resteAcceptee() {
        assertEquals(2, vues("""
            { "views": [ { "name": "Projet", "retain": ["Facture"] } ] }
            """).size());
    }


    @Test
    void leNomDuPaquetageParDefaut_estEnAnglais() {
        ImmutableList<UmlView> toutes = ConstructeurDeVues.construire(
            ImmutableSet.of(UmlEntity.builder("SansPaquetage", "SansPaquetage").build()),
            UmlDiagram.defaut(), Regles.lues("{ \"viewsByPackage\": true }", "test"));

        assertEquals(ConstructeurDeVues.PAQUETAGE_PAR_DEFAUT, toutes.get(1).name());
        assertEquals("default", toutes.get(1).nomDeFichier());
    }

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
        assertTrue(ConstructeurDeVues.construire(ImmutableSet.of(), UmlDiagram.defaut(),
            Regles.aucune()).isEmpty());
    }

    @Test
    void uneVueNeChangeNiLesBoitesNiLeursMembres() {
        UmlEntity dansLIntegrale = vues("{}").get(0).entities().iterator().next();
        UmlEntity dansLaVue =
            vue(uneVue("""
                { "name": "Deux", "retain": ["Facture", "Client"] }
                """), "Deux").entities().get(0);

        assertEquals(dansLIntegrale.name(), dansLaVue.name());
        assertEquals(dansLIntegrale.fields(), dansLaVue.fields());
        assertFalse(dansLaVue.relations().isEmpty());
    }

}

package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.UmlStyle;

// L'apparence du diagramme : le bloc de skinparam posé en tête du fichier PlantUML.
//
// Rien ici ne touche au sens du diagramme, seulement à sa lisibilité.
//
// Deux apparences cohabitent, décrites par UmlStyle et choisies par l'utilisateur. Ce qui les
// sépare est du goût ; ce qu'elles partagent — le bloc COMMUN — relève du sens, et n'est donc
// pas négociable. La frontière entre les deux est la seule décision intéressante de cette
// classe, et elle est écrite là plutôt que laissée à l'appréciation de chaque appelant.
public final class PlantUmlLayout {

    // Largeur moyenne d'un caractère, en pixels, pour la police et la taille choisies plus bas.
    // Approximation assumée : il ne s'agit que d'estimer un espacement, pas de mesurer un texte.
    private static final int LARGEUR_CARACTERE = 7;

    // Bornes de l'espacement horizontal entre deux boîtes voisines. En deçà du minimum les
    // étiquettes se chevauchent ; au-delà du maximum le diagramme s'étale sans rien y gagner.
    private static final int SEPARATION_MINIMALE = 60;

    private static final int SEPARATION_MAXIMALE = 220;

    // Espacement vertical entre deux rangs de boîtes. Constant : la hauteur d'une étiquette ne
    // varie pas, contrairement à sa largeur.
    private static final int SEPARATION_RANGS = 90;

    // Ce que les deux apparences partagent, parce que cela touche au sens du diagramme.
    //
    //   classAttributeIconSize 0 — les visibilités s'écrivent « + », « - », « # », « ~ », la
    //     notation d'UML, et non en pastilles colorées propres à PlantUML.
    //
    // Le style, en bloc de texte (text block, """ ... """) : plus lisible qu'une concaténation.
    private static final String COMMUN = """
        skinparam classAttributeIconSize 0
        """;

    // Le masquage des compartiments vides, posé par-dessus le bloc commun.
    //
    // Il ne vit pas dans COMMUN, et la nuance compte : il ne relève ni du goût — les deux
    // apparences le veulent — ni d'un invariant, puisque la ligne « show empty members » du
    // fichier de règles peut le lever. C'est un réglage du diagramme, décidé par le projet et non
    // par le style, qui n'a donc aucun avis là-dessus.
    //
    // Il est actif par défaut : la stratégie par défaut de @UMLClass ne montre aucun membre, si
    // bien que sans lui le cas nominal dessinerait des rectangles vides sous chaque nom.
    private static final String MASQUER_MEMBRES_VIDES = "hide empty members\n";

    // Le thème de la bibliothèque, ajouté au bloc commun quand UmlStyle.UMLG est demandé.
    //
    // « hide circle » supprime la pastille C/I/E accolée au nom : le mot-clé « interface » ou
    // « abstract class » écrit dans la boîte dit déjà la même chose. C'est un choix, et c'est
    // pourquoi il vit ici et non dans le bloc commun — sous UmlStyle.PLANTUML la pastille
    // revient, comme sur n'importe quel diagramme PlantUML.
    private static final String THEME_UMLG = """
        skinparam shadowing false
        skinparam roundCorner 8
        skinparam defaultFontName SansSerif
        skinparam defaultFontSize 13
        skinparam ArrowFontSize 12
        skinparam ArrowThickness 1.1
        skinparam ArrowColor #5C7A96
        skinparam ArrowFontColor #1A1A1A
        skinparam ClassBorderColor #33526E
        skinparam ClassBackgroundColor #FDFDFE
        skinparam ClassHeaderBackgroundColor #DDE7F0
        skinparam ClassFontStyle bold
        skinparam ClassFontSize 15
        skinparam ClassStereotypeFontSize 11
        hide circle
        """;

    private static final String COULEUR_NOM_DE_LIEN = "#3C5F82";

    private PlantUmlLayout() {}

    // Met en forme le nom d'une association posé au milieu du trait.
    //
    // L'italique est commun aux deux apparences, et pour une raison de lecture : la cardinalité
    // se pose au même endroit que le nom du lien, PlantUML seul décidant où, et rien d'autre ne
    // permettrait au lecteur de savoir lequel des deux textes nomme le trait. La couleur, elle,
    // appartient au thème — sous UmlStyle.PLANTUML le nom reste noir, comme le reste.
    public static String nomDeLien(UmlStyle style, String labelDejaEchappe) {
        checkNotNull(style);

        // Les balises plutôt que « //italique// » : un « // » présent dans le texte de
        // l'utilisateur fermerait le marqueur apparié, alors qu'il s'affiche tel quel entre
        // <i> et </i>. Les chevrons du texte, eux, sont déjà encodés par l'échappement.
        String italique = "<i>" + labelDejaEchappe + "</i>";

        return style == UmlStyle.UMLG
            ? "<color:" + COULEUR_NOM_DE_LIEN + ">" + italique + "</color>"
            : italique;
    }

    // Largeur qu'occupera approximativement une étiquette. Sert à décider de l'espacement.
    public static int largeurEstimee(String etiquette) {
        return etiquette.length() * LARGEUR_CARACTERE;
    }

    // Le bloc complet à insérer après @startuml. L'espacement horizontal dépend de la plus large
    // des étiquettes du diagramme : c'est elle qui risque de déborder sur la boîte voisine.
    //
    // L'espacement est commun aux deux apparences : il ne relève pas du goût mais de la place
    // qu'une étiquette occupe, et cette place est la même quelle que soit la couleur des boîtes.
    //
    // masquerMembresVides vient du fichier de règles, et traverse donc cette classe sans
    // appartenir au style : c'est le seul réglage de ce bloc que l'utilisateur décide lui-même.
    public static String bloc(UmlStyle style, int largeurEtiquetteMax,
        boolean masquerMembresVides) {
        checkNotNull(style);

        return COMMUN
            + (masquerMembresVides ? MASQUER_MEMBRES_VIDES : "")
            + (style == UmlStyle.UMLG ? THEME_UMLG : "")
            + "skinparam nodesep " + separationEntreVoisins(largeurEtiquetteMax) + "\n"
            + "skinparam ranksep " + SEPARATION_RANGS + "\n";
    }

    // clamp ramène la valeur dans l'intervalle : en dessous du minimum on prend le minimum,
    // au-dessus du maximum le maximum.
    private static int separationEntreVoisins(int largeurEtiquetteMax) {
        return Math.clamp(largeurEtiquetteMax, SEPARATION_MINIMALE, SEPARATION_MAXIMALE);
    }
}

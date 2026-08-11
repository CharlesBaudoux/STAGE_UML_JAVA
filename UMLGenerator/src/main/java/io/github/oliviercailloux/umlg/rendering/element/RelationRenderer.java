package io.github.oliviercailloux.umlg.rendering.element;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.rendering.syntax.AliasRegistry;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlLayout;
import java.util.Locale;

// Écrit les lignes de relation du fichier PlantUML.
//
// Forme produite, pour une association :
//   Client "1" *-down- "0..*" Commande : <i>passe</i>
//   ^ source  ^extrémité ^connecteur ^extrémité ^cible   ^label
//
// Cette classe décide de la forme du trait — flèche, losange, pointillé — et de l'ordre de ses
// morceaux ; le texte des extrémités est composé par Extremite.
//
// Contrairement aux autres renderers, elle s'instancie : elle retient au passage la largeur de
// la plus grande étiquette, dont PlantUmlLayout se sert pour calculer l'espacement du diagramme.
// Une instance par diagramme, donc.
public final class RelationRenderer {

    // L'apparence choisie, dont dépend l'habillage du nom d'un lien.
    private final UmlStyle style;

    // Accumulateur, mis à jour à chaque extrémité écrite.
    private int largeurEtiquetteMax;

    public RelationRenderer(UmlStyle style) {
        this.style = checkNotNull(style);
    }

    public String ligne(UmlEntity source, UmlRelation relation, String aliasSource,
        AliasRegistry registre) {

        // La cible doit avoir été déclarée. La désignation passée en second argument sert au
        // message d'erreur si elle ne l'a pas été.
        String aliasCible =
            registre.aliasCible(relation.targetQualifiedName(), designation(source, relation));

        // switch sur type (pattern matching) : UmlRelation étant sealed, le compilateur sait que
        // ces cas sont exhaustifs, et aucun « default » n'est nécessaire. Ajouter une nature de
        // relation ferait échouer la compilation ici — c'est l'effet recherché.
        String trait = switch (relation) {
            case UmlAssociation association -> trait(association, aliasSource, aliasCible);
            case UmlDependency dependance -> trait(dependance, aliasSource, aliasCible);
            case UmlInheritance heritage -> trait(heritage, aliasSource, aliasCible);
            case UmlLayout placement -> trait(placement, aliasSource, aliasCible);
            // La seule relation à trois boîtes : sa seconde extrémité demande une résolution de
            // plus, que les autres n'ont pas à subir.
            case UmlAssociationClass classe -> trait(classe, aliasSource, aliasCible,
                registre.aliasCible(classe.sourceQualifiedName(), designation(source, relation)));
        };

        // Le label est commun à toutes les natures de liens : le traiter une fois ici évite
        // qu'une nature nouvelle l'oublie, ou l'habille autrement que ses voisines.
        String nom = nomDeLien(relation.label());
        if (!nom.isEmpty()) {
            trait += " : " + nom;
            mesurer(relation.label());
        }
        return trait + "\n";
    }

    // Le nom du lien, habillé ligne par ligne.
    //
    // POURQUOI LE DÉCOUPAGE
    //
    // PlantUML coupe l'étiquette à chaque « \n » sans reporter les balises ouvertes. Un nom sur
    // deux lignes s'écrivait « <i>lig1\nlig2</i> » : la seconde ligne perdait son italique et
    // affichait « </i></color> » en toutes lettres sur le dessin. Rien ne l'attrapait — la source
    // est valide, donc la garde du writer ne voyait pas d'erreur. Chaque ligne porte donc
    // désormais son propre habillage.
    //
    // POURQUOI UNE LIGNE VIDE N'EST PAS HABILLÉE
    //
    // L'échappement peut vider une ligne — un texte fait de caractères de contrôle disparaît —,
    // et « <i></i> » se dessine « </i> » sur le trait. Une ligne vide ne reçoit donc rien, et un
    // nom entièrement vidé rend "", ce que l'appelant traduit par « pas de nom du tout ».
    private String nomDeLien(String label) {
        // -1 conserve les lignes vides finales, que le découpage supprimerait sinon.
        String[] lignes = label.split("\r\n|\r|\n", -1);

        StringBuilder sortie = new StringBuilder();
        boolean quelqueChose = false;
        for (int i = 0; i < lignes.length; i++) {
            if (i > 0) {
                sortie.append("\\n");
            }
            String echappee = PlantUmlEscaper.label(lignes[i]);
            if (!echappee.isEmpty()) {
                sortie.append(PlantUmlLayout.nomDeLien(style, echappee));
                quelqueChose = true;
            }
        }
        return quelqueChose ? sortie.toString() : "";
    }

    // Lue par PlantUmlSyntaxBuilder une fois toutes les relations écrites.
    public int largeurEtiquetteMax() {
        return largeurEtiquetteMax;
    }

    // Décrit la relation en français courant, pour les messages d'erreur : « The association
    // carried by com.banque.Client.commandes ».
    private static String designation(UmlEntity source, UmlRelation relation) {
        return switch (relation) {
            case UmlAssociation association ->
                "The association carried by " + source.qualifiedName() + "."
                    + association.fieldName();
            // La variable n'est pas utilisée : une dépendance n'a rien de plus à dire que sa
            // classe d'origine.
            case UmlDependency ignore -> "The dependency declared by " + source.qualifiedName();
            case UmlInheritance heritage -> switch (heritage.type()) {
                case GENERALIZATION ->
                    "The generalization declared by " + source.qualifiedName();
                case REALIZATION -> "The realization declared by " + source.qualifiedName();
            };
            case UmlLayout ignore -> "The layout declared by " + source.qualifiedName();
            case UmlAssociationClass ignore ->
                "The association class " + source.qualifiedName();
        };
    }

    // Un lien invisible : le trait existe pour le moteur de placement, et pour lui seul.
    //
    // La direction s'insère au milieu du connecteur, comme pour une association : le résultat
    // s'écrit « A -[hidden]down-> B ». Sans direction, le lien rapproche seulement les deux
    // boîtes sans dire de quel côté.
    private String trait(UmlLayout placement, String aliasSource, String aliasCible) {
        String direction = placement.direction() == UMLDirection.NONE
            ? ""
            : placement.direction().name().toLowerCase(Locale.ROOT);

        return aliasSource + " -[hidden]" + direction + "-> " + aliasCible;
    }

    // La classe d'association : « (Etudiant, Cours) .. Inscription ».
    //
    // Les deux extrémités entre parenthèses, la classe qui les qualifie après les pointillés.
    // Le trait entre les deux extrémités est dessiné par cette seule ligne : il n'y a pas à
    // déclarer en plus une association entre elles, ce qui doublerait le trait.
    //
    // La relation elle-même n'est pas lue : ses deux extrémités ont déjà été traduites en alias
    // par l'appelant, seul à disposer du registre.
    private String trait(UmlAssociationClass ignore, String aliasPorteur, String aliasCible,
        String aliasDepart) {
        return "(" + aliasDepart + ", " + aliasCible + ") .. " + aliasPorteur;
    }

    // Une dépendance se dessine toujours pareil : flèche pointillée, sans extrémités décorées.
    private String trait(UmlDependency dependance, String aliasSource, String aliasCible) {
        return aliasSource + " ..> " + aliasCible;
    }

    // L'héritage est la seule relation écrite à l'envers : la cible vient en premier.
    //
    // Ce n'est pas une coquetterie de syntaxe. Dans « A <|-- B », le sens du trait indique aussi
    // à PlantUML de placer A au-dessus de B — et un parent se dessine au-dessus de ses enfants.
    // La forme « B --|> A », pourtant équivalente pour le lecteur, retournerait la hiérarchie.
    //
    // Le triangle creux est commun aux deux natures ; seul le trait change : plein pour une
    // généralisation, pointillé pour une réalisation.
    private String trait(UmlInheritance heritage, String aliasSource, String aliasCible) {
        String connecteur = switch (heritage.type()) {
            case GENERALIZATION -> " <|-- ";
            case REALIZATION -> " <|.. ";
        };
        return aliasCible + connecteur + aliasSource;
    }

    // Une association s'assemble en quatre morceaux : source, extrémité source, connecteur,
    // extrémité cible, puis cible.
    private String trait(UmlAssociation association, String aliasSource, String aliasCible) {
        StringBuilder ligne = new StringBuilder(aliasSource);
        ajouterExtremite(ligne, Extremite.source(association));
        ligne.append(' ').append(connecteur(association));
        ajouterExtremite(ligne, Extremite.cible(association));
        return ligne.append(' ').append(aliasCible).toString();
    }

    // Ajoute une extrémité entre guillemets, et met à jour la largeur maximale observée.
    private void ajouterExtremite(StringBuilder ligne, Extremite.Etiquette extremite) {
        if (extremite.estVide()) {
            return;
        }
        ligne.append(" \"").append(extremite.ecrit()).append('"');
        mesurer(extremite.affiche());
    }

    // Retient la largeur du texte le plus large posé sur un trait.
    //
    // DEUX CORRECTIONS TIENNENT DANS CETTE MÉTHODE
    //
    // La mesure porte sur le texte tel qu'il sera dessiné, et non sur sa forme échappée : un
    // guillemet s'écrit « &#34; » dans la source et n'occupe qu'un caractère à l'écran. Mesurer
    // la source écartait les boîtes de cinq fois la place réellement nécessaire.
    //
    // Et elle porte sur tous les textes d'un trait, extrémités comme nom du lien. UmlStyle
    // annonce « l'espacement, calculé sur la plus longue étiquette du diagramme » ; seules les
    // extrémités étaient comptées, alors que le nom du lien est en pratique le texte le plus long
    // d'un trait, et qu'il se dessine exactement au même endroit — entre deux boîtes voisines.
    //
    // Un texte sur plusieurs lignes occupe la largeur de sa plus longue, et non leur somme : la
    // mesure se fait donc ligne à ligne.
    private void mesurer(String texteAffiche) {
        for (String ligne : texteAffiche.split("\r\n|\r|\n", -1)) {
            largeurEtiquetteMax =
                Math.max(largeurEtiquetteMax, PlantUmlLayout.largeurEstimee(ligne));
        }
    }

    // Le trait lui-même. La direction s'insère au milieu du connecteur : "-down->", "*-left-".
    // Locale.ROOT pour que DOWN donne toujours "down", quelle que soit la locale de la machine.
    private static String connecteur(UmlAssociation association) {
        String direction = association.direction() == UMLDirection.NONE
            ? ""
            : association.direction().name().toLowerCase(Locale.ROOT);

        return switch (association.type()) {
            // Flèche simple pour une association, losange creux (o) pour une agrégation, plein
            // (*) pour une composition — le losange se plaçant du côté du tout, ici la source.
            case ASSOCIATION -> "-" + direction + "->";
            case AGGREGATION -> "o-" + direction + "-";
            case COMPOSITION -> "*-" + direction + "-";
        };
    }

}

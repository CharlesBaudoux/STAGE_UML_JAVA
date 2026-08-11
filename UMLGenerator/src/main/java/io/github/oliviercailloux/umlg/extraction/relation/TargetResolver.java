package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

// Vérifie que la classe à l'autre bout du trait a le droit d'y être.
//
// La cible n'est jamais déduite : target() est un élément d'annotation sans valeur par défaut,
// donc javac refuse déjà toute @UMLAssociation qui ne la renseigne pas. Il ne reste ici que la
// validation, commune aux associations et aux dépendances.
//
// Public, non pour l'utilisateur — le paquetage n'est pas exporté — mais pour MemberExtractor :
// une association dessinée dans la boîte (UMLNotation.ATTRIBUTE) ne passe plus par
// AssociationExtractor, et sa cible doit pourtant être vérifiée par la même règle. La notation
// choisit une forme de dessin, elle ne relâche aucune exigence sur ce qui est dessiné.
public final class TargetResolver {

    private TargetResolver() {}

    public static void validerCible(Class<?> cible, String contexte, String parametre) {
        checkArgument(!cible.isPrimitive(),
            messageCiblePrimitive(contexte, cible.getName()));

        checkArgument(cible.isAnnotationPresent(UMLClass.class),
            messageCibleHorsDiagramme(contexte, cible.getName(), parametre));
    }

    // LES DEUX RÈGLES QUE LE PROCESSEUR D'ANNOTATIONS VIENT LIRE ICI
    //
    // Cible pose les mêmes questions à la compilation, sur une classe qui n'existe pas encore
    // sous forme chargée : le contrôle ne peut pas être partagé, mais le texte, lui, le doit.
    // Il était recopié mot pour mot, et c'est ainsi que deux messages voisins avaient fini par
    // dire deux choses différentes.

    // int.class est du Java légal — son type est Class<Integer> — et passe donc la vérification
    // du compilateur sans être pour autant une classe du diagramme.
    public static String messageCiblePrimitive(String contexte, String nomDuType) {
        return contexte + ": a relation cannot target the primitive type " + nomDuType
            + "; only a class of the diagram can be an endpoint.";
    }

    // @param parametre le nom du paramètre à corriger, « target() » ou « source() ».
    public static String messageCibleHorsDiagramme(String contexte, String nomDeLaCible,
        String parametre) {
        return contexte + ": target " + nomDeLaCible + " does not carry @UMLClass, so it is not "
            + "part of the diagram. Annotate it, or point " + parametre + " at a class that is.";
    }
}

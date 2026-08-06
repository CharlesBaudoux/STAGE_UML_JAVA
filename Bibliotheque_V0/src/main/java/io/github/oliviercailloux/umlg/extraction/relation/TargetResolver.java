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
            "%s: a relation cannot target the primitive type %s; only a class of the diagram "
                + "can be an endpoint.",
            contexte, cible.getName());

        checkArgument(cible.isAnnotationPresent(UMLClass.class),
            "%s: target %s does not carry @UMLClass, so it is not part of the diagram. "
                + "Annotate it, or point %s at a class that is.",
            contexte, cible.getName(), parametre);
    }
}

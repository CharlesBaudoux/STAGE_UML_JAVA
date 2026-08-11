package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;

// Relit l'@UMLAssociationClass posée sur la classe.
//
// Au plus une par classe, l'annotation n'étant pas répétable : une classe qualifie un lien, pas
// plusieurs. Les deux extrémités sont validées par la même règle que toutes les autres — une
// cible hors du diagramme ferait partir le trait de nulle part.
public final class AssociationClassExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlAssociationClass> extraire(Class<?> clazz) {
        UMLAssociationClass annotation = clazz.getAnnotation(UMLAssociationClass.class);
        if (annotation == null) {
            return ImmutableList.of();
        }
        String contexte = "@UMLAssociationClass sur " + clazz.getName();

        // La classe d'association est le troisième point du dessin : la voir revenir en
        // extrémité rattacherait le lien à lui-même.
        //
        // Avant la validation des extrémités, parce qu'une classe d'association qui se désigne
        // elle-même porte forcément @UMLClass : la seconde question n'aurait rien à dire, et
        // seule celle-ci explique la faute.
        checkArgument(annotation.source() != clazz,
            messageExtremiteReflexive(contexte, "source()"));
        checkArgument(annotation.target() != clazz,
            messageExtremiteReflexive(contexte, "target()"));

        TargetResolver.validerCible(annotation.source(), contexte, "source()");
        TargetResolver.validerCible(annotation.target(), contexte, "target()");

        return ImmutableList.of(new UmlAssociationClass(annotation.source().getName(),
            annotation.target().getName()));
    }

    // « Cette extrémité est la classe d'association elle-même », dit une seule fois.
    //
    // Le processeur d'annotations pose la même question à la compilation, et l'avait donc
    // réécrite : les deux textes avaient divergé, et les deux contrôles ne se faisaient même plus
    // dans le même ordre. La règle vit ici, avec l'extraction qui l'applique ; le processeur
    // vient la lire.
    //
    // @param membre le nom du paramètre fautif, « source() » ou « target() ».
    public static String messageExtremiteReflexive(String contexte, String membre) {
        return contexte + ": " + membre + " is the association class itself, and the association "
            + "class cannot be one of the two ends of the link it qualifies — the link would be "
            + "attached to the very box that qualifies it. Point it at the two classes the link "
            + "joins.";
    }
}

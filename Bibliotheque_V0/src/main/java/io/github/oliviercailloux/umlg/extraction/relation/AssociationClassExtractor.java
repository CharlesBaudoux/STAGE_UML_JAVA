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
        checkArgument(annotation.source() != clazz && annotation.target() != clazz,
            "%s: the association class cannot be one of the two ends of the link it qualifies.",
            contexte);

        TargetResolver.validerCible(annotation.source(), contexte, "source()");
        TargetResolver.validerCible(annotation.target(), contexte, "target()");

        return ImmutableList.of(new UmlAssociationClass(annotation.source().getName(),
            annotation.target().getName()));
    }
}

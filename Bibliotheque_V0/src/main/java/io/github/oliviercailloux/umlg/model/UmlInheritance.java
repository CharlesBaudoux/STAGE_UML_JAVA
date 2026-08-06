package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Un lien d'héritage : la traduction d'un « extends » ou d'un « implements ».
//
// C'est la seule relation que l'utilisateur ne déclare pas. Les deux autres viennent d'une
// annotation qu'il a écrite ; celle-ci se lit dans la signature de la classe, où elle est déjà
// dite une fois pour toutes. Il n'y a donc rien à paramétrer, et le record ne porte que le
// strict nécessaire : où va le trait, et de quelle nature il est.
public record UmlInheritance(String targetQualifiedName, UmlInheritanceType type)
    implements UmlRelation {

    public UmlInheritance {
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(type, "The inheritance type cannot be null.");
        checkArgument(!targetQualifiedName.isBlank(), "The target qualified name cannot be empty.");
    }

    // UmlRelation exige un label ; un lien d'héritage n'en porte jamais. La chaîne vide dit
    // l'absence, et RelationRenderer n'écrit alors rien après le trait.
    @Override
    public String label() {
        return "";
    }

    // Deux fabriques plutôt que le constructeur : sur le site d'appel, generalization(X) se lit
    // mieux que new UmlInheritance(X, GENERALIZATION), qui répète le type deux fois.
    public static UmlInheritance generalization(String targetQualifiedName) {
        return new UmlInheritance(targetQualifiedName, UmlInheritanceType.GENERALIZATION);
    }

    public static UmlInheritance realization(String targetQualifiedName) {
        return new UmlInheritance(targetQualifiedName, UmlInheritanceType.REALIZATION);
    }
}

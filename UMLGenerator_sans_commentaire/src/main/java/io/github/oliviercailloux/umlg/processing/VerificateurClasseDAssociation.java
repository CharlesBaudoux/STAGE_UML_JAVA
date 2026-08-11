package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

final class VerificateurClasseDAssociation {

    private final Rapporteur rapporteur;
    private final Cible cible;

    VerificateurClasseDAssociation(Rapporteur rapporteur, Cible cible) {
        this.rapporteur = rapporteur;
        this.cible = cible;
    }

    void verifier(TypeElement classe) {
        AnnotationMirror annotation = rapporteur.miroir(classe, UMLAssociationClass.class);
        if (annotation == null) {
            return;
        }
        String contexte = "@UMLAssociationClass on " + classe.getQualifiedName();

        if (classe.getAnnotation(UMLClass.class) == null) {
            rapporteur.erreur(contexte + ": the declaring class does not carry @UMLClass, so it "
                + "is not part of the diagram and the dashed line would reach an empty place. "
                + "Annotate it, or remove this annotation.", classe, annotation);
            return;
        }

        verifierExtremite(classe, annotation, contexte, "source");
        verifierExtremite(classe, annotation, contexte, "target");
    }

    private String verifierExtremite(TypeElement classe, AnnotationMirror annotation,
        String contexte, String membre) {
        String nom = cible.resoudre(annotation, membre, contexte, classe);
        if (nom == null) {
            return null;
        }

        if (nom.contentEquals(classe.getQualifiedName())) {
            rapporteur.erreur(contexte + ": " + membre + " is the association class itself; the "
                + "link would be attached to the very box that qualifies it. Point it at the two "
                + "classes the link joins.", classe, annotation,
                rapporteur.valeurEcrite(annotation, membre));
            return null;
        }
        return nom;
    }
}

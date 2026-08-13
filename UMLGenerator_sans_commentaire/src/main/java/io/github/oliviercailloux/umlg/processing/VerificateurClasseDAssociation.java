package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationClassExtractor;
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

        if (!rapporteur.exigerBoite(classe, contexte,
            "the dashed line would reach an empty place", classe, annotation)) {
            return;
        }

        verifierExtremite(classe, annotation, contexte, "source");
        verifierExtremite(classe, annotation, contexte, "target");
    }

    private void verifierExtremite(TypeElement classe, AnnotationMirror annotation,
        String contexte, String membre) {
        String ecrit = cible.lire(annotation, membre);

        if (ecrit != null && ecrit.contentEquals(classe.getQualifiedName())) {
            rapporteur.erreur(
                AssociationClassExtractor.messageExtremiteReflexive(contexte, membre + "()"),
                classe, annotation, rapporteur.valeurEcrite(annotation, membre));
            return;
        }
        cible.resoudre(annotation, membre, contexte, classe);
    }
}

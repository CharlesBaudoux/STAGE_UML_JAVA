package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

// Vérifie l'@UMLAssociationClass posée sur une classe.
//
// Deux extrémités au lieu d'une, mais les mêmes règles : chacune doit être une boîte du
// diagramme, et aucune ne peut être la classe d'association elle-même.
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

        // Les deux extrémités sont examinées séparément, et toutes les deux : l'auteur qui s'est
        // trompé sur l'une et sur l'autre doit les voir ensemble, plutôt que de recompiler pour
        // découvrir la seconde.
        //
        // Rien n'est dit du cas où elles désignent la même classe : une association réflexive
        // existe en UML — deux personnes mariées l'une à l'autre —, et la classe qui la qualifie
        // est alors parfaitement licite.
        verifierExtremite(classe, annotation, contexte, "source");
        verifierExtremite(classe, annotation, contexte, "target");
    }

    // Rend le nom complet de l'extrémité, ou null si elle a été refusée — auquel cas l'erreur
    // a déjà été signalée.
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

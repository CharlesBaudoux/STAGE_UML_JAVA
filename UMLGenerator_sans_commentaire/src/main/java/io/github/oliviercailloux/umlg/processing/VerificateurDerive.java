package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

final class VerificateurDerive {

    private final Rapporteur rapporteur;
    private final Strategie strategie;

    VerificateurDerive(Rapporteur rapporteur, Strategie strategie) {
        this.rapporteur = rapporteur;
        this.strategie = strategie;
    }

    void verifier(Element membre) {
        AnnotationMirror annotation = rapporteur.miroir(membre, UMLDerived.class);
        if (annotation == null) {
            return;
        }
        TypeElement porteuse = (TypeElement) membre.getEnclosingElement();
        if (!rapporteur.exigerBoite(porteuse, Membre.emplacement(membre),
            "this annotation has no effect", membre, annotation)) {
            return;
        }
        UMLClass classe = porteuse.getAnnotation(UMLClass.class);

        AnnotationMirror association = rapporteur.miroir(membre, UMLAssociation.class);
        if (association != null) {
            if (Notation.trait(rapporteur, association)) {
                signalerLienSansRole(membre, annotation, association);
            }
            return;
        }
        signalerMembreInvisible(membre, classe, annotation);
    }

    private void signalerLienSansRole(Element membre, AnnotationMirror annotation,
        AnnotationMirror association) {
        if (!rapporteur.texte(association, "targetRole").isEmpty()) {
            return;
        }
        rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLDerived marks the link as "
            + "derived, and the slash goes in front of the target role — but targetRole is not "
            + "set on @UMLAssociation, so nothing on the link would carry it. Set targetRole, or "
            + "remove @UMLDerived.", membre, annotation);
    }

    private void signalerMembreInvisible(Element membre, UMLClass classe,
        AnnotationMirror annotation) {
        if (strategie.membreAffiche(membre, classe) != Strategie.Affichage.MASQUE) {
            return;
        }
        rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLDerived has no visible "
            + "effect, this member is not shown on the diagram (strategy = "
            + strategie.effective(classe)
            + " on the declaring class). Add @UMLInclude if you want to see it, slash included.",
            membre, annotation);
    }
}

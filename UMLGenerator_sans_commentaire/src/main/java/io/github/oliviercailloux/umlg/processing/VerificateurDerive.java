package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
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
        UMLClass classe = porteuse.getAnnotation(UMLClass.class);

        if (classe == null) {
            rapporteur.erreur(Membre.emplacement(membre) + ": the declaring class does not carry "
                + "@UMLClass, so it is not part of the diagram and this annotation has no "
                + "effect. Annotate " + porteuse.getQualifiedName() + ", or remove it.",
                membre, annotation);
            return;
        }

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
        if (!strategie.connue(classe)) {
            return;
        }
        if (Membre.vientDUnComposant(membre)) {
            return;
        }
        boolean visible = VisibilityPolicy.estVisible(Membre.bits(membre),
            strategie.effective(classe),
            rapporteur.miroir(membre, UMLInclude.class) != null,
            rapporteur.miroir(membre, UMLExclude.class) != null);

        if (visible) {
            return;
        }
        rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLDerived has no visible "
            + "effect, this member is not shown on the diagram (strategy = "
            + strategie.effective(classe)
            + " on the declaring class). Add @UMLInclude if you want to see it, slash included.",
            membre, annotation);
    }
}

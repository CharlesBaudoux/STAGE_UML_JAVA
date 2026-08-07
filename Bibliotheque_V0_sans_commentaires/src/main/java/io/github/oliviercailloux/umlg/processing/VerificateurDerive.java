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

// Vérifie @UMLDerived, posée sur un attribut ou une méthode.
//
// L'annotation ne porte aucun paramètre : il n'y a donc rien à valider dans ce qu'elle dit, et
// tout à vérifier dans ce qu'elle produira. Une barre oblique ne se voit que si l'élément
// qu'elle marque est dessiné ; ce vérificateur ne parle que de cela.
final class VerificateurDerive {

    private final Rapporteur rapporteur;

    VerificateurDerive(Rapporteur rapporteur) {
        this.rapporteur = rapporteur;
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
            signalerLienSansRole(membre, annotation, association);
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
        boolean visible = VisibilityPolicy.estVisible(Membre.bits(membre), classe.strategy(),
            rapporteur.miroir(membre, UMLInclude.class) != null,
            rapporteur.miroir(membre, UMLExclude.class) != null);

        if (visible) {
            return;
        }
        rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLDerived has no visible "
            + "effect, this member is not shown on the diagram (strategy = " + classe.strategy()
            + " on the declaring class). Add @UMLInclude if you want to see it, slash included.",
            membre, annotation);
    }
}

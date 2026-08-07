package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

// Vérifie les deux dérogations, @UMLInclude et @UMLExclude, posées sur un attribut ou une
// méthode.
final class VerificateurMembre {

    private final Rapporteur rapporteur;

    VerificateurMembre(Rapporteur rapporteur) {
        this.rapporteur = rapporteur;
    }

    void verifier(Element membre) {
        AnnotationMirror inclusion = rapporteur.miroir(membre, UMLInclude.class);
        AnnotationMirror exclusion = rapporteur.miroir(membre, UMLExclude.class);

        if (inclusion != null && exclusion != null) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries both @UMLInclude and "
                + "@UMLExclude: the intent is contradictory. Keep the one that says what you "
                + "mean.", membre, exclusion);
            return;
        }

        AnnotationMirror presente = inclusion != null ? inclusion : exclusion;
        if (presente == null) {
            return;
        }

        TypeElement porteuse = (TypeElement) membre.getEnclosingElement();
        UMLClass classe = porteuse.getAnnotation(UMLClass.class);
        if (classe == null) {
            rapporteur.erreur(Membre.emplacement(membre) + ": the declaring class does not "
                + "carry @UMLClass, so it is not part of the diagram and this annotation has no "
                + "effect. Annotate " + porteuse.getQualifiedName() + ", or remove it.",
                membre, presente);
            return;
        }

        signalerDerogationSansEffet(membre, classe, inclusion != null, presente);
    }

    private void signalerDerogationSansEffet(Element membre, UMLClass classe, boolean inclusion,
        AnnotationMirror presente) {

        boolean association = rapporteur.miroir(membre, UMLAssociation.class) != null;

        boolean visibleSansDerogation =
            VisibilityPolicy.estVisible(Membre.bits(membre), classe.strategy(), false, false);

        if (inclusion) {
            if (association) {
                rapporteur.avertissement(Membre.emplacement(membre) + ": this field also carries "
                    + "@UMLAssociation, so it is drawn as a link between boxes and never inside "
                    + "one; @UMLInclude cannot bring it back into the attribute compartment. "
                    + "Remove one of the two annotations.", membre, presente);
                return;
            }
            if (Membre.estAttribut(membre) && visibleSansDerogation) {
                rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude has no "
                    + "effect, the field is already shown by strategy = " + classe.strategy()
                    + " on the declaring class. Remove it, or narrow the strategy.",
                    membre, presente);
            }
            return;
        }

        if (association) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": this field also carries "
                + "@UMLAssociation, so @UMLExclude removes the link between the two boxes, not "
                + "just a line inside one. Remove @UMLAssociation instead if that is what you "
                + "meant.", membre, presente);
            return;
        }
        if (!visibleSansDerogation) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLExclude has no effect, "
                + "strategy = " + classe.strategy() + " on the declaring class already hides "
                + "this member.", membre, presente);
        }
    }
}

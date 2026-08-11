package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

final class VerificateurMembre {

    private final Rapporteur rapporteur;
    private final Strategie strategie;

    VerificateurMembre(Rapporteur rapporteur, Strategie strategie) {
        this.rapporteur = rapporteur;
        this.strategie = strategie;
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

        if (inclusion != null && !verifierRenommage(membre, inclusion)) {
            return;
        }
        if (inclusion != null && !verifierNotationAttribut(membre, inclusion)) {
            return;
        }

        signalerDerogationSansEffet(membre, classe, inclusion != null, presente);
    }

    private boolean verifierRenommage(Element membre, AnnotationMirror inclusion) {
        String nom = membre.getAnnotation(UMLInclude.class).name();
        if (nom.isEmpty()) {
            return true;
        }
        if (nom.isBlank()) {
            rapporteur.erreur(Membre.emplacement(membre) + ": @UMLInclude(name = ...) cannot be "
                + "made of whitespace only. Write a real name, or leave the attribute out.",
                membre, inclusion);
            return false;
        }
        AnnotationMirror lien = rapporteur.miroir(membre, UMLAssociation.class);
        if (lien != null && !Notation.trait(rapporteur, lien)
            && !membre.getAnnotation(UMLAssociation.class).targetRole().isEmpty()) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries both targetRole = \""
                + membre.getAnnotation(UMLAssociation.class).targetRole()
                + "\" and @UMLInclude(name = \"" + nom + "\"): two names for the same line. "
                + "Keep only one of the two.", membre, inclusion);
            return false;
        }
        if (nom.equals(membre.getSimpleName().toString())) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude(name = \"" + nom
                + "\") renames this member to the name it already has. Remove the attribute.",
                membre, inclusion);
        }
        return true;
    }

    private boolean verifierNotationAttribut(Element membre, AnnotationMirror inclusion) {
        if (!membre.getAnnotation(UMLInclude.class).asAttribute()) {
            return true;
        }
        if (membre.getKind() != ElementKind.METHOD) {
            rapporteur.erreur(Membre.emplacement(membre) + ": @UMLInclude(asAttribute = true) is "
                + "for methods; a field is already an attribute. Remove the attribute.",
                membre, inclusion);
            return false;
        }
        ExecutableElement methode = (ExecutableElement) membre;
        if (!methode.getParameters().isEmpty()) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries @UMLInclude(asAttribute = "
                + "true) but takes parameters: an attribute has none. Remove the parameters, or "
                + "the notation.", membre, inclusion);
            return false;
        }
        if (methode.getReturnType().getKind() == TypeKind.VOID) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries @UMLInclude(asAttribute = "
                + "true) but returns nothing: an attribute is its type. Return something, or "
                + "remove the notation.", membre, inclusion);
            return false;
        }
        return true;
    }

    private void signalerDerogationSansEffet(Element membre, UMLClass classe, boolean inclusion,
        AnnotationMirror presente) {

        AnnotationMirror lien = rapporteur.miroir(membre, UMLAssociation.class);
        boolean association = lien != null;
        boolean trait = association && Notation.trait(rapporteur, lien);

        if (Membre.vientDUnComposant(membre)) {
            return;
        }

        boolean strategieConnue = strategie.connue(classe);
        boolean visibleSansDerogation = strategieConnue
            && VisibilityPolicy.estVisible(Membre.bits(membre), strategie.effective(classe),
                false, false);

        if (inclusion) {
            boolean renomme = !membre.getAnnotation(UMLInclude.class).name().isEmpty();

            if (trait) {
                rapporteur.avertissement(Membre.emplacement(membre) + ": this field also carries "
                    + "@UMLAssociation, so it is drawn as a link between boxes and never inside "
                    + "one; @UMLInclude cannot bring it back into the attribute compartment. "
                    + "Remove one of the two annotations, or set notation = ATTRIBUTE on the "
                    + "association.", membre, presente);
                return;
            }
            if (association) {
                if (!renomme) {
                    rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude has no "
                        + "effect, this field carries @UMLAssociation(notation = ATTRIBUTE) and "
                        + "is therefore already shown in the attribute compartment, whatever "
                        + "the strategy. Remove it.", membre, presente);
                }
                return;
            }
            if (!renomme && strategieConnue && Membre.estAttribut(membre)
                && visibleSansDerogation
                && !strategie.champMasqueParUneRegle(membre.getSimpleName().toString())) {
                rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude has no "
                    + "effect, the field is already shown by strategy = "
                    + strategie.effective(classe)
                    + " on the declaring class. Remove it, or narrow the strategy.",
                    membre, presente);
            }
            return;
        }

        if (trait) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": this field also carries "
                + "@UMLAssociation, so @UMLExclude removes the link between the two boxes, not "
                + "just a line inside one. Remove @UMLAssociation instead if that is what you "
                + "meant.", membre, presente);
            return;
        }
        if (association) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": this field also carries "
                + "@UMLAssociation(notation = ATTRIBUTE), so @UMLExclude removes the very line "
                + "that association asked for, and the target class keeps no trace of it. Remove "
                + "@UMLAssociation instead if that is what you meant.", membre, presente);
            return;
        }
        if (strategieConnue && !visibleSansDerogation) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLExclude has no effect, "
                + "strategy = " + strategie.effective(classe)
                + " on the declaring class already hides "
                + "this member.", membre, presente);
        }
    }
}

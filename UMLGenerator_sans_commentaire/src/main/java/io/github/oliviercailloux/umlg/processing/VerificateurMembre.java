package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
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
        if (!rapporteur.exigerBoite(porteuse, Membre.emplacement(membre),
            "this annotation has no effect", membre, presente)) {
            return;
        }
        UMLClass classe = porteuse.getAnnotation(UMLClass.class);

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
        String emplacement = Membre.emplacement(membre);

        if (membre.getKind() != ElementKind.METHOD) {
            rapporteur.erreur(MemberExtractor.messageNotationAttributSurUnChamp(emplacement),
                membre, inclusion);
            return false;
        }
        ExecutableElement methode = (ExecutableElement) membre;
        if (!methode.getParameters().isEmpty()) {
            rapporteur.erreur(MemberExtractor.messageNotationAttributAvecParametres(emplacement),
                membre, inclusion);
            return false;
        }
        if (methode.getReturnType().getKind() == TypeKind.VOID) {
            rapporteur.erreur(MemberExtractor.messageNotationAttributSansRetour(emplacement),
                membre, inclusion);
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

        Strategie.Affichage sansDerogation =
            strategie.membreAfficheSansDerogation(membre, classe);

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
                if (!renomme && strategie.reglesConnues()
                    && !strategie.champMasqueParUneRegle(membre.getSimpleName().toString())) {
                    rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude has no "
                        + "effect, this field carries @UMLAssociation(notation = ATTRIBUTE) and "
                        + "is therefore already shown in the attribute compartment, whatever "
                        + "the strategy. Remove it.", membre, presente);
                }
                return;
            }
            if (!renomme && strategie.reglesConnues() && Membre.estAttribut(membre)
                && sansDerogation == Strategie.Affichage.AFFICHE
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
        if (sansDerogation == Strategie.Affichage.MASQUE) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLExclude has no effect, "
                + "strategy = " + strategie.effective(classe)
                + " on the declaring class already hides "
                + "this member.", membre, presente);
        }
    }
}

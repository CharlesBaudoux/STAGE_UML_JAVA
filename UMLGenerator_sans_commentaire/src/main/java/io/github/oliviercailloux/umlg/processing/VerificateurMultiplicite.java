package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.model.Multiplicites;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

final class VerificateurMultiplicite {

    private final Rapporteur rapporteur;
    private final Strategie strategie;

    VerificateurMultiplicite(Rapporteur rapporteur, Strategie strategie) {
        this.rapporteur = rapporteur;
        this.strategie = strategie;
    }

    void verifier(Element membre) {
        AnnotationMirror annotation = rapporteur.miroir(membre, UMLMultiplicity.class);
        if (annotation == null) {
            return;
        }
        TypeElement porteuse = (TypeElement) membre.getEnclosingElement();
        if (!rapporteur.exigerBoite(porteuse, Membre.emplacement(membre),
            "this annotation has no effect", membre, annotation)) {
            return;
        }
        UMLClass classe = porteuse.getAnnotation(UMLClass.class);

        if (!verifierValeur(membre, annotation)) {
            return;
        }

        if (rapporteur.miroir(membre, UMLAssociation.class) != null) {
            rapporteur.erreur(
                MemberExtractor.messageDeuxMultiplicites(Membre.emplacement(membre)),
                membre, annotation);
            return;
        }
        signalerMembreInvisible(membre, classe, annotation);
    }

    private boolean verifierValeur(Element membre, AnnotationMirror annotation) {
        String valeur = rapporteur.texte(annotation, "value");

        if (valeur.isEmpty()) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLMultiplicity(\"\") asks "
                + "for nothing; an empty multiplicity is what a member carries when it has none. "
                + "Write the multiplicity, or remove the annotation.", membre, annotation);
            return false;
        }

        try {
            Multiplicites.valider(valeur, "multiplicity");
            return true;
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(Membre.emplacement(membre) + " : " + e.getMessage(), membre,
                annotation, rapporteur.valeurEcrite(annotation, "value"));
            return false;
        }
    }

    private void signalerMembreInvisible(Element membre, UMLClass classe,
        AnnotationMirror annotation) {
        if (strategie.membreAffiche(membre, classe) != Strategie.Affichage.MASQUE) {
            return;
        }
        rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLMultiplicity has no visible "
            + "effect, this member is not shown on the diagram (strategy = "
            + strategie.effective(classe)
            + " on the declaring class). Add @UMLInclude if you want to see it, brackets "
            + "included.", membre, annotation);
    }
}

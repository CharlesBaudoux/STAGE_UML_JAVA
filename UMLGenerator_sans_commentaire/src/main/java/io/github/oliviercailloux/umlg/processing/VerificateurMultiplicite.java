package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
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
        UMLClass classe = porteuse.getAnnotation(UMLClass.class);

        if (classe == null) {
            rapporteur.erreur(Membre.emplacement(membre) + ": the declaring class does not carry "
                + "@UMLClass, so it is not part of the diagram and this annotation has no "
                + "effect. Annotate " + porteuse.getQualifiedName() + ", or remove it.",
                membre, annotation);
            return;
        }

        if (!verifierValeur(membre, annotation)) {
            return;
        }

        if (rapporteur.miroir(membre, UMLAssociation.class) != null) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries both @UMLAssociation and "
                + "@UMLMultiplicity: the multiplicity of this property is already given by "
                + "targetCardinality. Keep only one of the two.", membre, annotation);
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
            Multiplicites.valider(valeur, "cardinality");
            return true;
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(Membre.emplacement(membre) + " : " + e.getMessage(), membre,
                annotation, rapporteur.valeurEcrite(annotation, "value"));
            return false;
        }
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
        rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLMultiplicity has no visible "
            + "effect, this member is not shown on the diagram (strategy = "
            + strategie.effective(classe)
            + " on the declaring class). Add @UMLInclude if you want to see it, brackets "
            + "included.", membre, annotation);
    }
}

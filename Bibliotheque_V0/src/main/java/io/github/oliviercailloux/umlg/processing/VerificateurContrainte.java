package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLConstraints;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

// Vérifie les @UMLConstraint, posées sur une classe ou un attribut.
final class VerificateurContrainte {

    private final Rapporteur rapporteur;
    private final Strategie strategie;

    VerificateurContrainte(Rapporteur rapporteur, Strategie strategie) {
        this.rapporteur = rapporteur;
        this.strategie = strategie;
    }

    void verifier(Element porteur) {
        List<AnnotationMirror> contraintes =
            rapporteur.repetees(porteur, UMLConstraint.class, UMLConstraints.class);
        if (contraintes.isEmpty()) {
            return;
        }
        boolean surClasse = porteur.getKind().isClass() || porteur.getKind().isInterface();
        TypeElement boite = surClasse
            ? (TypeElement) porteur
            : (TypeElement) porteur.getEnclosingElement();
        String contexte = "@UMLConstraint on "
            + (surClasse ? boite.getQualifiedName().toString() : Membre.emplacement(porteur));

        UMLClass classe = boite.getAnnotation(UMLClass.class);
        if (classe == null) {
            rapporteur.erreur(contexte + ": " + boite.getQualifiedName() + " does not carry "
                + "@UMLClass, so it is not part of the diagram and this constraint would be "
                + "written nowhere. Annotate it, or remove this constraint.",
                porteur, contraintes.get(0));
            return;
        }

        for (AnnotationMirror contrainte : contraintes) {
            verifierRegle(porteur, contrainte, contexte);
        }

        if (!surClasse) {
            signalerAttributInvisible(porteur, classe, contraintes.get(0), contexte);
        }
    }

    private void verifierRegle(Element porteur, AnnotationMirror contrainte, String contexte) {
        String regle = rapporteur.texte(contrainte, "rule");

        if (regle.isBlank()) {
            rapporteur.erreur(contexte + ": rule() cannot be empty or made of whitespace only.",
                porteur, contrainte, rapporteur.valeurEcrite(contrainte, "rule"));
            return;
        }

        // Même méprise que pour le stéréotype et ses guillemets : la notation d'UML est
        // tellement associée aux accolades qu'on les écrit sans y penser. Elles seraient alors
        // dessinées en double, sans que rien n'échoue.
        if (regle.startsWith("{") || regle.endsWith("}")) {
            rapporteur.avertissement(contexte + ": rule() = \"" + regle + "\" already carries the "
                + "braces, which the generator adds itself; the diagram would show them twice. "
                + "Write the rule bare.", porteur, contrainte,
                rapporteur.valeurEcrite(contrainte, "rule"));
        }
    }

    // Une contrainte s'écrit à la suite de l'attribut qu'elle vise : si l'attribut n'est pas
    // dessiné, elle ne l'est pas davantage.
    //
    // Sauf s'il porte @UMLAssociation : la contrainte part alors sur le lien, qui est dessiné,
    // lui. C'est la règle d'arbitrage de la spécification, et le cas majoritaire — {subset},
    // {ordered}. Rien à signaler.
    private void signalerAttributInvisible(Element attribut, UMLClass classe,
        AnnotationMirror contrainte, String contexte) {

        if (rapporteur.miroir(attribut, UMLAssociation.class) != null) {
            return;
        }

        // Sans les règles du projet, une stratégie laissée à PROJECT est hors de portée de
        // javac : le contrôle se tait plutôt que de deviner. L'option -Aumlg.rules la lui donne.
        if (!strategie.connue(classe)) {
            return;
        }
        boolean visible = VisibilityPolicy.estVisible(Membre.bits(attribut),
            strategie.effective(classe),
            rapporteur.miroir(attribut, UMLInclude.class) != null,
            rapporteur.miroir(attribut, UMLExclude.class) != null);

        if (visible) {
            return;
        }
        rapporteur.avertissement(contexte + ": this field is not shown on the diagram "
            + "(strategy = "
            + strategie.effective(classe) + " on the declaring class), so the constraint "
            + "will not be written anywhere. Add @UMLInclude, or move the constraint onto the "
            + "class.", attribut, contrainte);
    }
}

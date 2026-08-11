package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLConstraints;
import io.github.oliviercailloux.umlg.model.Contraintes;
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
        String emplacement =
            surClasse ? boite.getQualifiedName().toString() : Membre.emplacement(porteur);
        String contexte = "@UMLConstraint on " + emplacement;

        UMLClass classe = boite.getAnnotation(UMLClass.class);
        if (!rapporteur.exigerBoite(boite, contexte, "this constraint would be written nowhere",
            porteur, contraintes.get(0))) {
            return;
        }

        for (int rang = 0; rang < contraintes.size(); rang++) {
            // Le rang est écrit dans le texte parce que javac ne peut pas le montrer : plusieurs
            // @UMLConstraint sont emballées dans un conteneur synthétique, sans position source.
            verifierRegle(porteur, contraintes.get(rang), Rapporteur.contexteDeLaRepetee(
                "UMLConstraint", emplacement, rang, contraintes.size()));
        }

        if (!surClasse) {
            signalerAttributInvisible(porteur, classe, contraintes.get(0), contexte);
        }
    }

    private void verifierRegle(Element porteur, AnnotationMirror contrainte, String contexte) {
        String regle = rapporteur.texte(contrainte, "rule");

        // La règle est confiée au modèle, seul détenteur de ce qu'est une contrainte : elle n'est
        // pas réécrite ici, et le message affiché par javac est mot pour mot celui que la
        // génération aurait produit. Les deux avaient déjà divergé.
        try {
            Contraintes.valider(regle);
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(contexte + " : " + e.getMessage(),
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
        if (strategie.membreAffiche(attribut, classe) != Strategie.Affichage.MASQUE) {
            return;
        }
        rapporteur.avertissement(contexte + ": this field is not shown on the diagram "
            + "(strategy = "
            + strategie.effective(classe) + " on the declaring class), so the constraint "
            + "will not be written anywhere. Add @UMLInclude, or move the constraint onto the "
            + "class.", attribut, contrainte);
    }
}

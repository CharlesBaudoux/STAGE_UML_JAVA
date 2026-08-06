package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
import io.github.oliviercailloux.umlg.model.UmlField;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

// Vérifie @UMLMultiplicity, posée sur un attribut ou une méthode.
//
// Trois questions, dans cet ordre : la valeur est-elle une multiplicité ? l'annotation n'en
// contredit-elle pas une autre ? et le membre qu'elle qualifie sera-t-il seulement dessiné ?
final class VerificateurMultiplicite {

    private final Rapporteur rapporteur;

    VerificateurMultiplicite(Rapporteur rapporteur) {
        this.rapporteur = rapporteur;
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

        // Une propriété n'a qu'une multiplicité. Sur un attribut porteur de @UMLAssociation,
        // c'est targetCardinality qui la donne — quelle que soit la notation, puisque le lien la
        // porte aussi bien à l'extrémité d'un trait qu'à la suite d'une ligne.
        if (rapporteur.miroir(membre, UMLAssociation.class) != null) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries both @UMLAssociation and "
                + "@UMLMultiplicity: the multiplicity of this property is already given by "
                + "targetCardinality. Keep only one of the two.", membre, annotation);
            return;
        }
        signalerMembreInvisible(membre, classe, annotation);
    }

    // La valeur est confiée au modèle, seul détenteur de la grammaire des multiplicités : la
    // règle n'est pas réécrite ici, et le message est mot pour mot celui de la génération.
    //
    // Une multiplicité vide est licite pour le modèle — c'est ainsi qu'on dit « rien à
    // afficher » — mais l'écrire dans une annotation revient à ne rien demander du tout.
    private boolean verifierValeur(Element membre, AnnotationMirror annotation) {
        String valeur = rapporteur.texte(annotation, "value");

        if (valeur.isEmpty()) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLMultiplicity(\"\") asks "
                + "for nothing; an empty multiplicity is what a member carries when it has none. "
                + "Write the multiplicity, or remove the annotation.", membre, annotation);
            return false;
        }

        try {
            new UmlField("m", "T", valeur, "+", false, false, ImmutableList.of());
            return true;
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(Membre.emplacement(membre) + " : " + e.getMessage(), membre,
                annotation, rapporteur.valeurEcrite(annotation, "value"));
            return false;
        }
    }

    // Des crochets ne se voient que si le membre qu'ils suivent est dessiné.
    //
    // Comme pour @UMLDerived, la question ne se pose que sur les attributs et les méthodes que la
    // stratégie masque à coup sûr : le filtre anti-bruit, que le processeur n'a pas sous la main,
    // ne fait qu'en retirer davantage.
    private void signalerMembreInvisible(Element membre, UMLClass classe,
        AnnotationMirror annotation) {
        boolean visible = VisibilityPolicy.estVisible(Membre.bits(membre), classe.strategy(),
            rapporteur.miroir(membre, UMLInclude.class) != null,
            rapporteur.miroir(membre, UMLExclude.class) != null);

        if (visible) {
            return;
        }
        rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLMultiplicity has no visible "
            + "effect, this member is not shown on the diagram (strategy = " + classe.strategy()
            + " on the declaring class). Add @UMLInclude if you want to see it, brackets "
            + "included.", membre, annotation);
    }
}

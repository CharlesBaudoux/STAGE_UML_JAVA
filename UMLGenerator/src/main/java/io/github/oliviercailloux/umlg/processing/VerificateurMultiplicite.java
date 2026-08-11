package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.model.Multiplicites;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

// Vérifie @UMLMultiplicity, posée sur un attribut ou une méthode.
//
// Trois questions, dans cet ordre : la valeur est-elle une multiplicité ? l'annotation n'en
// contredit-elle pas une autre ? et le membre qu'elle qualifie sera-t-il seulement dessiné ?
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
    //
    // La question était posée en construisant un UmlField jetable, seule façon d'atteindre une
    // grammaire qui vivait sur UmlAssociation, et qui n'était pas visible d'ici. Le détour
    // donnait la bonne réponse, mais l'aurait perdue au premier contrôle ajouté au constructeur
    // d'UmlField : l'erreur aurait parlé du nom ou du type là où l'utilisateur avait écrit une
    // multiplicité. Multiplicites.valider pose la question directement.
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

    // Des crochets ne se voient que si le membre qu'ils suivent est dessiné.
    //
    // Comme pour @UMLDerived, la question ne se pose que sur les attributs et les méthodes que la
    // stratégie masque à coup sûr : le filtre anti-bruit, que le processeur n'a pas sous la main,
    // ne fait qu'en retirer davantage.
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

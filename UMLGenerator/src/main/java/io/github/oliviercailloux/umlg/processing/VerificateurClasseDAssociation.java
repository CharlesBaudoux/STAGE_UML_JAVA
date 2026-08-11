package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationClassExtractor;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

// Vérifie l'@UMLAssociationClass posée sur une classe.
//
// Deux extrémités au lieu d'une, mais les mêmes règles : chacune doit être une boîte du
// diagramme, et aucune ne peut être la classe d'association elle-même.
final class VerificateurClasseDAssociation {

    private final Rapporteur rapporteur;
    private final Cible cible;

    VerificateurClasseDAssociation(Rapporteur rapporteur, Cible cible) {
        this.rapporteur = rapporteur;
        this.cible = cible;
    }

    void verifier(TypeElement classe) {
        AnnotationMirror annotation = rapporteur.miroir(classe, UMLAssociationClass.class);
        if (annotation == null) {
            return;
        }
        String contexte = "@UMLAssociationClass on " + classe.getQualifiedName();

        if (!rapporteur.exigerBoite(classe, contexte,
            "the dashed line would reach an empty place", classe, annotation)) {
            return;
        }

        // Les deux extrémités sont examinées séparément, et toutes les deux : l'auteur qui s'est
        // trompé sur l'une et sur l'autre doit les voir ensemble, plutôt que de recompiler pour
        // découvrir la seconde.
        //
        // Rien n'est dit du cas où elles désignent la même classe : une association réflexive
        // existe en UML — deux personnes mariées l'une à l'autre —, et la classe qui la qualifie
        // est alors parfaitement licite.
        verifierExtremite(classe, annotation, contexte, "source");
        verifierExtremite(classe, annotation, contexte, "target");
    }

    // Une extrémité, dans le même ordre et avec le même message que la génération.
    //
    // « Est-ce la classe d'association elle-même ? » d'abord, « est-ce une boîte du diagramme ? »
    // ensuite : c'est l'ordre d'AssociationClassExtractor, et l'ordre compte, puisqu'une classe
    // d'association qui se désigne elle-même porte forcément @UMLClass — la seconde question
    // n'aurait alors rien à dire, et la première seule explique la faute. Le message vient de
    // l'extracteur, à qui la règle appartient : les deux avaient divergé.
    private void verifierExtremite(TypeElement classe, AnnotationMirror annotation,
        String contexte, String membre) {
        String ecrit = cible.lire(annotation, membre);

        if (ecrit != null && ecrit.contentEquals(classe.getQualifiedName())) {
            rapporteur.erreur(
                AssociationClassExtractor.messageExtremiteReflexive(contexte, membre + "()"),
                classe, annotation, rapporteur.valeurEcrite(annotation, membre));
            return;
        }
        cible.resoudre(annotation, membre, contexte, classe);
    }
}

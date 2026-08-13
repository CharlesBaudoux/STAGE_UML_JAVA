package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLDependencies;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.relation.DependencyExtractor;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

final class VerificateurDependance {

    private final Rapporteur rapporteur;
    private final Cible cible;

    VerificateurDependance(Rapporteur rapporteur, Cible cible) {
        this.rapporteur = rapporteur;
        this.cible = cible;
    }

    void verifier(TypeElement classe) {
        List<AnnotationMirror> dependances =
            rapporteur.repetees(classe, UMLDependency.class, UMLDependencies.class);
        if (dependances.isEmpty()) {
            return;
        }
        String emplacement = classe.getQualifiedName().toString();
        String contexte = "@UMLDependency on " + emplacement;

        if (!rapporteur.exigerBoite(classe, contexte,
            "this dependency would be drawn from nowhere", classe, dependances.get(0))) {
            return;
        }

        Set<UmlDependency> deja = new HashSet<>();
        ImmutableSet<String> parAssociation = ciblesDesAssociations(classe);

        for (int rang = 0; rang < dependances.size(); rang++) {
            AnnotationMirror annotation = dependances.get(rang);
            String contexteDeLaDependance = Rapporteur.contexteDeLaRepetee("UMLDependency",
                emplacement, rang, dependances.size());
            String nomCible =
                cible.resoudre(annotation, "target", contexteDeLaDependance, classe);
            if (nomCible == null) {
                continue;
            }

            if (nomCible.contentEquals(classe.getQualifiedName())) {
                rapporteur.erreur(
                    DependencyExtractor.messageDependanceReflexive(contexteDeLaDependance),
                    classe, annotation, rapporteur.valeurEcrite(annotation, "target"));
                continue;
            }

            UmlDependency dependance;
            try {
                dependance = new UmlDependency(nomCible, rapporteur.texte(annotation, "label"));
            } catch (IllegalArgumentException e) {
                rapporteur.erreur(contexteDeLaDependance + " : " + e.getMessage(), classe,
                    annotation);
                continue;
            }

            if (!deja.add(dependance)) {
                rapporteur.erreur(DependencyExtractor.messageDependanceEnDouble(
                    contexteDeLaDependance, nomCible), classe, annotation);
                continue;
            }

            if (parAssociation.contains(nomCible)) {
                rapporteur.avertissement(contexteDeLaDependance + ": an @UMLAssociation of this "
                    + "class already links it to " + nomCible + ". A dependency says strictly "
                    + "less than an association, so the second line would only clutter the "
                    + "diagram.",
                    classe, annotation, rapporteur.valeurEcrite(annotation, "target"));
            }
        }
    }

    private ImmutableSet<String> ciblesDesAssociations(TypeElement classe) {
        ImmutableSet.Builder<String> cibles = ImmutableSet.builder();
        for (Element membre : classe.getEnclosedElements()) {
            if (membre.getKind() != ElementKind.FIELD) {
                continue;
            }
            AnnotationMirror association = rapporteur.miroir(membre, UMLAssociation.class);
            if (association == null || !Notation.trait(rapporteur, association)) {
                continue;
            }
            String nom = cible.lire(association, "target");
            if (nom != null) {
                cibles.add(nom);
            }
        }
        return cibles.build();
    }

}

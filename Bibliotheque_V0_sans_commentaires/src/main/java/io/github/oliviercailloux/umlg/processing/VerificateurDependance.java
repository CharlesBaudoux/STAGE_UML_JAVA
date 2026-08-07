package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDependencies;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

// Vérifie les @UMLDependency posées sur une classe, y compris répétées.
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
        String contexte = "@UMLDependency on " + classe.getQualifiedName();

        if (classe.getAnnotation(UMLClass.class) == null) {
            rapporteur.erreur(contexte + ": the declaring class does not carry @UMLClass, so it "
                + "is not part of the diagram and this dependency would be drawn from nowhere. "
                + "Annotate it, or remove this annotation.", classe, dependances.get(0));
            return;
        }

        Set<UmlDependency> deja = new HashSet<>();
        ImmutableSet<String> parAssociation = ciblesDesAssociations(classe);

        for (AnnotationMirror annotation : dependances) {
            String nomCible = cible.resoudre(annotation, "target", contexte, classe);
            if (nomCible == null) {
                continue;
            }

            if (nomCible.contentEquals(classe.getQualifiedName())) {
                rapporteur.erreur(contexte + ": a class cannot depend on itself; the relation "
                    + "would teach the diagram's reader nothing.", classe, annotation,
                    rapporteur.valeurEcrite(annotation, "target"));
                continue;
            }

            UmlDependency dependance;
            try {
                dependance = new UmlDependency(nomCible, rapporteur.texte(annotation, "label"));
            } catch (IllegalArgumentException e) {
                rapporteur.erreur(contexte + " : " + e.getMessage(), classe, annotation);
                continue;
            }

            if (!deja.add(dependance)) {
                rapporteur.erreur(contexte + ": the dependency towards " + nomCible + " is "
                    + "declared twice with the same label; the two lines would overlap with "
                    + "nothing to show it.", classe, annotation);
                continue;
            }

            if (parAssociation.contains(nomCible)) {
                rapporteur.avertissement(contexte + ": an @UMLAssociation of this class already "
                    + "links it to " + nomCible + ". A dependency says strictly less than an "
                    + "association, so the second line would only clutter the diagram.",
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
            if (association == null) {
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

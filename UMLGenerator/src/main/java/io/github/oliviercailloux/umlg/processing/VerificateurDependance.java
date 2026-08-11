package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
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
        String emplacement = classe.getQualifiedName().toString();
        String contexte = "@UMLDependency on " + emplacement;

        if (!rapporteur.exigerBoite(classe, contexte,
            "this dependency would be drawn from nowhere", classe, dependances.get(0))) {
            return;
        }

        // Le même ensemble que celui de DependencyExtractor, pour la même raison : deux lignes
        // superposées, rien pour les distinguer.
        Set<UmlDependency> deja = new HashSet<>();
        // Calculé une fois pour toutes les dépendances de la classe.
        ImmutableSet<String> parAssociation = ciblesDesAssociations(classe);

        for (int rang = 0; rang < dependances.size(); rang++) {
            AnnotationMirror annotation = dependances.get(rang);
            // Le rang est écrit dans le texte parce que javac ne peut pas le montrer : plusieurs
            // @UMLDependency sont emballées dans un conteneur synthétique, sans position source,
            // et le diagnostic se pose alors sur la déclaration de classe entière.
            String contexteDeLaDependance = Rapporteur.contexteDeLaRepetee("UMLDependency",
                emplacement, rang, dependances.size());
            String nomCible =
                cible.resoudre(annotation, "target", contexteDeLaDependance, classe);
            if (nomCible == null) {
                continue;
            }

            if (nomCible.contentEquals(classe.getQualifiedName())) {
                rapporteur.erreur(contexteDeLaDependance + ": a class cannot depend on itself; "
                    + "the relation would teach the diagram's reader nothing.", classe, annotation,
                    rapporteur.valeurEcrite(annotation, "target"));
                continue;
            }

            UmlDependency dependance;
            try {
                // Comme pour les associations, la règle sur le label n'est pas réécrite : elle
                // est appliquée par le constructeur du modèle, seul à la détenir.
                dependance = new UmlDependency(nomCible, rapporteur.texte(annotation, "label"));
            } catch (IllegalArgumentException e) {
                rapporteur.erreur(contexteDeLaDependance + " : " + e.getMessage(), classe,
                    annotation);
                continue;
            }

            if (!deja.add(dependance)) {
                rapporteur.erreur(contexteDeLaDependance + ": the dependency towards " + nomCible
                    + " is declared twice with the same label; the two lines would overlap with "
                    + "nothing to show it.", classe, annotation);
                continue;
            }

            if (parAssociation.contains(nomCible)) {
                // Une association dit déjà tout ce qu'une dépendance dirait, et davantage. Les
                // deux traits partiraient de la même boîte vers la même boîte, le second
                // n'ajoutant que de l'encombrement.
                rapporteur.avertissement(contexteDeLaDependance + ": an @UMLAssociation of this "
                    + "class already links it to " + nomCible + ". A dependency says strictly "
                    + "less than an association, so the second line would only clutter the "
                    + "diagram.",
                    classe, annotation, rapporteur.valeurEcrite(annotation, "target"));
            }
        }
    }

    // Les classes que les @UMLAssociation de cette classe relient déjà par un trait.
    //
    // Celles dessinées dans la boîte (notation = ATTRIBUTE) n'en sont pas : elles ne tirent aucun
    // trait, et une dépendance vers la même classe est alors le seul lien du dessin — elle
    // n'encombre rien et ne doit pas être signalée.
    //
    // Lues sans rien signaler : leurs fautes éventuelles regardent VerificateurAssociation, et
    // les rapporter ici les afficherait deux fois. Une cible illisible est simplement absente de
    // l'ensemble, ce qui fait taire l'avertissement — la bonne prudence, puisque l'erreur qui
    // compte est déjà signalée ailleurs.
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

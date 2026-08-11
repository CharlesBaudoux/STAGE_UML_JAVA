package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import java.util.HashSet;
import java.util.Set;

// Relit les @UMLDependency posées sur la classe elle-même.
//
// Plus simple que l'extraction des associations : il n'y a rien à déduire. Le seul travail est de
// détecter les doublons, qu'aucun attribut ne distingue ici — deux dépendances identiques
// dessineraient deux traits superposés.
public final class DependencyExtractor implements RelationExtractor {

    // Les règles du projet ne disent rien des dépendances : elles se lisent sur les annotations
    // de la classe, dans l'ordre où l'utilisateur les a écrites.
    @Override
    public ImmutableList<UmlDependency> extraire(Class<?> clazz, Regles regles) {
        ImmutableList.Builder<UmlDependency> result = ImmutableList.builder();

        Set<UmlDependency> deja = new HashSet<>();

        // getAnnotationsByType déballe le conteneur @UMLDependencies créé par @Repeatable : que
        // l'utilisateur en ait posé une ou dix, on récupère un tableau uniforme.
        for (UMLDependency annotation : clazz.getAnnotationsByType(UMLDependency.class)) {
            Class<?> cible = annotation.target();
            String contexte = "@UMLDependency sur " + clazz.getName();

            checkArgument(cible != clazz, messageDependanceReflexive(contexte));

            // La cible doit exister dans le diagramme, donc porter @UMLClass.
            TargetResolver.validerCible(cible, contexte, "target()");

            UmlDependency dependance;
            try {
                dependance = new UmlDependency(cible.getName(), annotation.label());
            } catch (IllegalArgumentException | NullPointerException e) {

                throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
            }

            checkArgument(deja.add(dependance),
                messageDependanceEnDouble(contexte, cible.getName()));

            result.add(dependance);
        }
        return result.build();
    }

    // LES DEUX RÈGLES QUE LE PROCESSEUR D'ANNOTATIONS VIENT LIRE ICI
    //
    // VerificateurDependance pose les mêmes questions à la compilation, et recopiait les deux
    // textes mot pour mot. Ils vivent désormais là où la règle s'applique.

    public static String messageDependanceReflexive(String contexte) {
        return contexte + ": a class cannot depend on itself; the relation would teach the "
            + "diagram's reader nothing.";
    }

    public static String messageDependanceEnDouble(String contexte, String nomDeLaCible) {
        return contexte + ": the dependency towards " + nomDeLaCible + " is declared twice with "
            + "the same label; the two lines would overlap with nothing to show it.";
    }
}

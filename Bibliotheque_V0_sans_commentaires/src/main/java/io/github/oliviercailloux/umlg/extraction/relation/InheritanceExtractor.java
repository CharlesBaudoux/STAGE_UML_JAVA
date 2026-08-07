package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import java.util.Arrays;

// Relit la signature de la classe — « extends », « implements » — et en tire les liens
// d'héritage.
//
// Seul extracteur à ne rien devoir à une annotation : ce que l'utilisateur a écrit en Java dit
// déjà tout. Cela change la façon de traiter une cible absente du diagramme. Les deux autres
// extracteurs refusent une cible sans @UMLClass, et ils ont raison : cette cible a été écrite
// exprès, l'oubli est une faute. Ici au contraire, une classe mère hors du diagramme est le cas
// courant — tout hérite d'Object, beaucoup de classes implémentent Serializable ou Comparable.
// Le lien est donc simplement ignoré, sans erreur : seul le périmètre déclaré par @UMLClass
// fait foi.
public final class InheritanceExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlInheritance> extraire(Class<?> clazz) {
        ImmutableList.Builder<UmlInheritance> result = ImmutableList.builder();

        Class<?> parent = clazz.getSuperclass();
        if (parent != null && faitPartieDuDiagramme(parent)) {
            result.add(UmlInheritance.generalization(parent.getName()));
        }

        for (Class<?> interfaceDirecte : clazz.getInterfaces()) {
            if (!faitPartieDuDiagramme(interfaceDirecte)) {
                continue;
            }
            result.add(estUneSpecialisation(clazz, interfaceDirecte)
                ? UmlInheritance.generalization(interfaceDirecte.getName())
                : UmlInheritance.realization(interfaceDirecte.getName()));
        }
        return result.build();
    }

    private static boolean estUneSpecialisation(Class<?> source, Class<?> cible) {
        return source.isInterface() || estPermisePar(source, cible);
    }

    private static boolean estPermisePar(Class<?> source, Class<?> cible) {
        if (!cible.isSealed()) {
            return false;
        }
        Class<?>[] permises = cible.getPermittedSubclasses();
        if (permises == null) {
            return false;
        }
        return Arrays.asList(permises).contains(source);
    }

    private static boolean faitPartieDuDiagramme(Class<?> candidat) {
        return candidat.isAnnotationPresent(UMLClass.class);
    }
}

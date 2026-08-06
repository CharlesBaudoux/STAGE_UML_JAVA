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

        // getSuperclass rend null pour une interface et pour Object ; pour une énumération ou un
        // record, elle rend Enum ou Record, que le filtre écarte ensuite comme n'importe quelle
        // autre classe non annotée.
        Class<?> parent = clazz.getSuperclass();
        if (parent != null && faitPartieDuDiagramme(parent)) {
            result.add(UmlInheritance.generalization(parent.getName()));
        }

        // getInterfaces ne rend que les interfaces directement citées, sans les interfaces de ces
        // interfaces. C'est exactement ce qu'UML demande : les liens transitifs se lisent de
        // proche en proche sur le dessin, les redessiner surchargerait le diagramme.
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

    // Le lien vers une interface est-il une généralisation, ou une réalisation ?
    //
    // Java écrit « implements » dans les deux cas, et la réflexion range les deux dans
    // getInterfaces() : il faut donc reconstituer la distinction. Deux situations la méritent.
    //
    // Une interface qui en étend une autre, d'abord : le mot-clé Java est « extends », et elle
    // hérite bel et bien de la structure de sa cible. C'est une généralisation, et Java le dit.
    //
    // Une hiérarchie scellée, ensuite. « sealed interface KeysymEntry permits Mnemonic, Ucp,
    // Code » ne décrit pas un contrat que n'importe qui pourrait honorer : elle énumère les
    // seules formes qu'un KeysymEntry puisse prendre. C'est la définition même d'une
    // spécialisation — la cible est le cas général, les types permis en sont les cas
    // particuliers, et la liste est fermée. Une réalisation dirait l'inverse : que l'ensemble
    // des implémentations est ouvert.
    //
    // Le langage porte déjà l'information, aucune annotation n'est donc nécessaire pour trancher.
    private static boolean estUneSpecialisation(Class<?> source, Class<?> cible) {
        return source.isInterface() || estPermisePar(source, cible);
    }

    // La source figure-t-elle dans le « permits » de la cible ?
    //
    // getPermittedSubclasses rend null pour un type non scellé ; le test sur isSealed le rend
    // superflu, mais un extracteur qui suppose ne fait que déplacer la panne.
    private static boolean estPermisePar(Class<?> source, Class<?> cible) {
        if (!cible.isSealed()) {
            return false;
        }
        Class<?>[] permises = cible.getPermittedSubclasses();
        if (permises == null) {
            return false;
        }
        // Comparaison par identité : les deux classes viennent du même chargeur, celui que
        // ProjectScanner a enraciné sur le répertoire analysé.
        return Arrays.asList(permises).contains(source);
    }

    private static boolean faitPartieDuDiagramme(Class<?> candidat) {
        return candidat.isAnnotationPresent(UMLClass.class);
    }
}

package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.relation.TargetResolver;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

// Vérifie la classe désignée par un paramètre de type Class<?>, et en rend le nom complet.
//
// C'est l'équivalent, au moment de la compilation, de TargetResolver.validerCible. Le contrôle
// ne peut pas être partagé avec lui : TargetResolver reçoit un Class<?> déjà chargé, alors
// qu'ici la classe visée n'existe pas encore sous forme compilée. Les messages, eux, le sont —
// ils sont écrits une seule fois, dans TargetResolver, à qui les règles appartiennent ; les
// recopier ici, fût-ce à l'identique, était une divergence en sursis.
final class Cible {

    private final Rapporteur rapporteur;
    private final Types types;

    Cible(Rapporteur rapporteur, Types types) {
        this.rapporteur = rapporteur;
        this.types = types;
    }

    // Rend le nom complet de la classe visée, ou null si elle ne peut pas être une extrémité.
    // Dans ce second cas l'erreur a déjà été signalée : l'appelant n'a qu'à s'arrêter.
    String resoudre(AnnotationMirror annotation, String membre, String contexte, Element porteur) {
        // Le repli ne sert qu'à @UMLAssociation : partout ailleurs, la valeur est obligatoire et
        // javac refuse déjà l'annotation qui l'omet. La plainte n'a donc jamais à être dite.
        return resoudre(annotation, membre, contexte, porteur, null,
            contexte + ": " + membre + "() is not written, and it has no default.");
    }

    // La même chose, avec un repli quand la valeur n'est pas écrite.
    //
    // Sert à @UMLAssociation, dont le target est facultatif depuis que le type de l'attribut
    // suffit à le dire. Le repli est la classe que ce type désigne ; s'il n'en désigne aucune, la
    // plainte — celle-là même que la génération ferait entendre, mot pour mot — demande d'écrire
    // la cible plutôt que de laisser deviner.
    String resoudre(AnnotationMirror annotation, String membre, String contexte, Element porteur,
        TypeMirror repli, String plainteSansRepli) {
        AnnotationValue valeur = rapporteur.valeur(annotation, membre);
        AnnotationValue position = rapporteur.valeurEcrite(annotation, membre);

        // Pour un membre de type Class<?>, getValue() rend un TypeMirror : la description que
        // javac se fait du type, sans avoir eu besoin de le charger.
        TypeMirror cible = (TypeMirror) valeur.getValue();

        // void est la sentinelle « rien d'écrit » : on se rabat alors sur ce que dit le type.
        if (cible.getKind() == TypeKind.VOID) {
            if (repli == null) {
                rapporteur.erreur(plainteSansRepli, porteur, annotation);
                return null;
            }
            cible = repli;
        }

        // int.class est du Java légal — son type est Class<Integer> —, et passerait donc la
        // vérification du compilateur sans être pour autant une classe du diagramme.
        if (cible.getKind().isPrimitive()) {
            rapporteur.erreur(TargetResolver.messageCiblePrimitive(contexte, cible.toString()),
                porteur, annotation, position);
            return null;
        }

        // Écarte void.class, les tableaux, et tout ce qui n'est pas une classe ou une interface.
        if (cible.getKind() != TypeKind.DECLARED) {
            rapporteur.erreur(contexte + ": " + cible + " is not a class; only a class of the "
                + "diagram can be an endpoint.", porteur, annotation, position);
            return null;
        }

        TypeElement element = (TypeElement) types.asElement(cible);

        if (element.getAnnotation(UMLClass.class) == null) {
            rapporteur.erreur(TargetResolver.messageCibleHorsDiagramme(contexte,
                element.getQualifiedName().toString(), membre + "()"),
                porteur, annotation, position);
            return null;
        }
        return element.getQualifiedName().toString();
    }

    // Le nom complet de la classe visée, sans rien signaler, ou null si le membre ne désigne pas
    // une classe.
    //
    // Sert aux vérifications qui lisent une annotation dont elles ne sont pas responsables : le
    // contrôle des dépendances regarde les @UMLAssociation de la classe pour repérer les
    // doublons, mais c'est VerificateurAssociation qui a la charge de leurs erreurs. Les
    // signaler ici les afficherait deux fois.
    String lire(AnnotationMirror annotation, String membre) {
        AnnotationValue valeur = rapporteur.valeur(annotation, membre);
        if (valeur == null || !(valeur.getValue() instanceof TypeMirror type)
            || type.getKind() != TypeKind.DECLARED) {
            return null;
        }
        return ((TypeElement) types.asElement(type)).getQualifiedName().toString();
    }
}

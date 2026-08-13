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

final class Cible {

    private final Rapporteur rapporteur;
    private final Types types;

    Cible(Rapporteur rapporteur, Types types) {
        this.rapporteur = rapporteur;
        this.types = types;
    }

    String resoudre(AnnotationMirror annotation, String membre, String contexte, Element porteur) {
        return resoudre(annotation, membre, contexte, porteur, null,
            contexte + ": " + membre + "() is not written, and it has no default.");
    }

    String resoudre(AnnotationMirror annotation, String membre, String contexte, Element porteur,
        TypeMirror repli, String plainteSansRepli) {
        AnnotationValue valeur = rapporteur.valeur(annotation, membre);
        AnnotationValue position = rapporteur.valeurEcrite(annotation, membre);

        TypeMirror cible = (TypeMirror) valeur.getValue();

        if (cible.getKind() == TypeKind.VOID) {
            if (repli == null) {
                rapporteur.erreur(plainteSansRepli, porteur, annotation);
                return null;
            }
            cible = repli;
        }

        if (cible.getKind().isPrimitive()) {
            rapporteur.erreur(TargetResolver.messageCiblePrimitive(contexte, cible.toString()),
                porteur, annotation, position);
            return null;
        }

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

    String lire(AnnotationMirror annotation, String membre) {
        AnnotationValue valeur = rapporteur.valeur(annotation, membre);
        if (valeur == null || !(valeur.getValue() instanceof TypeMirror type)
            || type.getKind() != TypeKind.DECLARED) {
            return null;
        }
        return ((TypeElement) types.asElement(type)).getQualifiedName().toString();
    }
}

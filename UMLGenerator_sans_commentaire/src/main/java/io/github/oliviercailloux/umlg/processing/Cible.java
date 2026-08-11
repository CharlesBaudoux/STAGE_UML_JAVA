package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
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
        return resoudre(annotation, membre, contexte, porteur, null);
    }

    String resoudre(AnnotationMirror annotation, String membre, String contexte, Element porteur,
        TypeMirror repli) {
        AnnotationValue valeur = rapporteur.valeur(annotation, membre);
        AnnotationValue position = rapporteur.valeurEcrite(annotation, membre);

        TypeMirror cible = (TypeMirror) valeur.getValue();

        if (cible.getKind() == TypeKind.VOID) {
            if (repli == null) {
                rapporteur.erreur(contexte + ": target() is not written, and the field's type "
                    + "does not name a single class of the diagram. Write it explicitly.",
                    porteur, annotation);
                return null;
            }
            cible = repli;
        }

        if (cible.getKind().isPrimitive()) {
            rapporteur.erreur(contexte + ": a relation cannot target the primitive type " + cible
                + "; only a class of the diagram can be an endpoint.",
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
            rapporteur.erreur(contexte + ": target " + element.getQualifiedName()
                + " does not carry @UMLClass, so it is not part of the diagram. Annotate it, "
                + "or point " + membre + "() at a class that is.", porteur, annotation, position);
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

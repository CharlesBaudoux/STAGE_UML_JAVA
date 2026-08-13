package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

final class Rapporteur {

    private final Messager messager;
    private final Elements elements;
    private final boolean avertir;

    Rapporteur(Messager messager, Elements elements, boolean avertir) {
        this.messager = messager;
        this.elements = elements;
        this.avertir = avertir;
    }

    void erreur(String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message);
    }

    void avertissementDeConfiguration(String message) {
        messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, message);
    }

    void erreur(String message, Element element, AnnotationMirror annotation,
        AnnotationValue valeur) {
        signaler(Diagnostic.Kind.ERROR, message, element, annotation, valeur);
    }

    void erreur(String message, Element element, AnnotationMirror annotation) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation);
    }

    void avertissement(String message, Element element, AnnotationMirror annotation,
        AnnotationValue valeur) {
        if (!avertir) {
            return;
        }
        signaler(Diagnostic.Kind.WARNING, message, element, annotation, valeur);
    }

    void avertissement(String message, Element element, AnnotationMirror annotation) {
        if (!avertir) {
            return;
        }
        messager.printMessage(Diagnostic.Kind.WARNING, message, element, annotation);
    }

    boolean exigerBoite(TypeElement boite, String contexte, String consequence, Element porteur,
        AnnotationMirror annotation) {
        if (boite.getAnnotation(UMLClass.class) != null) {
            return true;
        }
        erreur(contexte + ": " + boite.getQualifiedName() + " does not carry @UMLClass, so it is "
            + "not part of the diagram and " + consequence + ". Annotate it, or remove this "
            + "annotation.", porteur, annotation);
        return false;
    }

    private void signaler(Diagnostic.Kind gravite, String message, Element element,
        AnnotationMirror annotation, AnnotationValue valeur) {
        if (valeur == null) {
            messager.printMessage(gravite, message, element, annotation);
            return;
        }
        messager.printMessage(gravite, message, element, annotation, valeur);
    }

    AnnotationMirror miroir(Element element, Class<? extends Annotation> type) {
        String recherche = type.getCanonicalName();
        for (AnnotationMirror candidat : element.getAnnotationMirrors()) {
            if (candidat.getAnnotationType().toString().equals(recherche)) {
                return candidat;
            }
        }
        return null;
    }

    ImmutableList<AnnotationMirror> repetees(Element element, Class<? extends Annotation> type,
        Class<? extends Annotation> conteneur) {
        ImmutableList.Builder<AnnotationMirror> resultat = ImmutableList.builder();

        AnnotationMirror seule = miroir(element, type);
        if (seule != null) {
            resultat.add(seule);
        }

        AnnotationMirror groupe = miroir(element, conteneur);
        if (groupe != null) {
            for (Object valeur : (List<?>) valeur(groupe, "value").getValue()) {
                resultat.add((AnnotationMirror) ((AnnotationValue) valeur).getValue());
            }
        }
        return resultat.build();
    }

    static String contexteDeLaRepetee(String nomAnnotation, String emplacement, int rang,
        int total) {
        String annotation = total == 1
            ? "@" + nomAnnotation
            : "the " + ordinal(rang + 1) + " @" + nomAnnotation;
        return annotation + " on " + emplacement;
    }

    private static String ordinal(int rang) {
        String suffixe = switch (rang % 100 >= 11 && rang % 100 <= 13 ? 0 : rang % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return rang + suffixe;
    }

    AnnotationValue valeur(AnnotationMirror annotation, String membre) {
        return chercher(elements.getElementValuesWithDefaults(annotation), membre);
    }

    AnnotationValue valeurEcrite(AnnotationMirror annotation, String membre) {
        return chercher(annotation.getElementValues(), membre);
    }

    String texte(AnnotationMirror annotation, String membre) {
        AnnotationValue valeur = valeur(annotation, membre);
        return valeur == null ? "" : String.valueOf(valeur.getValue());
    }

    private static AnnotationValue chercher(
        Map<? extends ExecutableElement, ? extends AnnotationValue> valeurs, String membre) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entree
            : valeurs.entrySet()) {
            if (entree.getKey().getSimpleName().contentEquals(membre)) {
                return entree.getValue();
            }
        }
        return null;
    }
}

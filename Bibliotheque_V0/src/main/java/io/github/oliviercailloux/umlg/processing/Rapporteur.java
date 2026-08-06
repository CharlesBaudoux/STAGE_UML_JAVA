package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

// Écrit les erreurs dans javac, et lit les annotations sans les instancier.
//
// Deux services, réunis parce qu'ils répondent au même besoin : parler d'une annotation présente
// dans du code que javac est en train de compiler.
//
// Sur la lecture. Un processeur ne peut pas appeler annotation.target() : la classe visée n'est
// peut-être pas encore compilée, et l'appel lève MirroredTypeException. On passe donc par les
// « miroirs » — AnnotationMirror, AnnotationValue —, la vue que javac offre sur une annotation
// qu'il n'a pas encore chargée. C'est aussi ce qui permet de positionner l'erreur : un miroir
// connaît sa place dans le fichier source, une instance d'annotation non.
final class Rapporteur {

    private final Messager messager;
    private final Elements elements;
    // Faux quand l'utilisateur a écrit -Aumlg.warnings=none : les erreurs restent, les
    // avertissements se taisent. Voir UmlgProcessor.getSupportedOptions.
    private final boolean avertir;

    Rapporteur(Messager messager, Elements elements, boolean avertir) {
        this.messager = messager;
        this.elements = elements;
        this.avertir = avertir;
    }

    // Erreur positionnée sur une valeur précise : « target = Commande.class » sera souligné.
    void erreur(String message, Element element, AnnotationMirror annotation,
        AnnotationValue valeur) {
        signaler(Diagnostic.Kind.ERROR, message, element, annotation, valeur);
    }

    // Erreur positionnée sur l'annotation entière.
    void erreur(String message, Element element, AnnotationMirror annotation) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation);
    }

    // Avertissement positionné sur une valeur précise.
    //
    // La distinction avec erreur() n'est pas une nuance de ton : une erreur dit « ce code ne
    // décrit aucun diagramme », un avertissement dit « ce code décrit un diagramme, mais
    // vraisemblablement pas celui que tu crois ». Le second cas ne peut pas arrêter la
    // compilation — la bibliothèque n'est pas juge des intentions —, mais le taire laisserait
    // l'utilisateur découvrir la surprise sur l'image, sans indice sur sa cause.
    void avertissement(String message, Element element, AnnotationMirror annotation,
        AnnotationValue valeur) {
        if (!avertir) {
            return;
        }
        signaler(Diagnostic.Kind.WARNING, message, element, annotation, valeur);
    }

    // Avertissement positionné sur l'annotation entière.
    void avertissement(String message, Element element, AnnotationMirror annotation) {
        if (!avertir) {
            return;
        }
        messager.printMessage(Diagnostic.Kind.WARNING, message, element, annotation);
    }

    // Le tronc commun aux deux gravités : une valeur absente du source ne peut pas être
    // soulignée, on se rabat alors sur l'annotation entière — seule chose que le lecteur voie.
    private void signaler(Diagnostic.Kind gravite, String message, Element element,
        AnnotationMirror annotation, AnnotationValue valeur) {
        if (valeur == null) {
            messager.printMessage(gravite, message, element, annotation);
            return;
        }
        messager.printMessage(gravite, message, element, annotation, valeur);
    }

    // Le miroir d'une annotation donnée sur un élément, ou null si elle n'y est pas.
    //
    // La comparaison porte sur le nom complet du type de l'annotation : c'est le seul point de
    // rencontre entre la classe Java que nous connaissons et le type que javac manipule.
    AnnotationMirror miroir(Element element, Class<? extends Annotation> type) {
        String recherche = type.getCanonicalName();
        for (AnnotationMirror candidat : element.getAnnotationMirrors()) {
            if (candidat.getAnnotationType().toString().equals(recherche)) {
                return candidat;
            }
        }
        return null;
    }

    // Un miroir par annotation répétable écrite, qu'elle soit seule ou répétée.
    //
    // Quand l'utilisateur en pose plusieurs, le compilateur les emballe dans le conteneur et
    // l'annotation isolée disparaît : il faut donc regarder les deux cas. Le membre « value » du
    // conteneur est un tableau, d'où la liste d'AnnotationValue dont chaque élément enveloppe un
    // miroir.
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

    // La valeur d'un membre, défauts compris : getElementValues() ne rend que ce qui est
    // effectivement écrit, ce qui ferait manquer tous les paramètres laissés à leur défaut.
    AnnotationValue valeur(AnnotationMirror annotation, String membre) {
        return chercher(elements.getElementValuesWithDefaults(annotation), membre);
    }

    // La valeur seulement si elle est écrite dans le source. Sert au positionnement : souligner
    // un défaut n'aurait aucun sens, puisqu'il n'apparaît pas dans le fichier.
    AnnotationValue valeurEcrite(AnnotationMirror annotation, String membre) {
        return chercher(annotation.getElementValues(), membre);
    }

    // Raccourci de lecture pour les membres textuels, dont on veut la chaîne et non l'objet.
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

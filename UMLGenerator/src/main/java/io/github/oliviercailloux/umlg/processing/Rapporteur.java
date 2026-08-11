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

    // Message rattaché à aucun élément du code, parce qu'il n'en vise aucun : une option de
    // compilation mal renseignée, un fichier de règles introuvable. javac l'affiche seul, sans
    // fichier ni numéro de ligne — c'est exact, la faute n'est pas dans le code source.
    void erreur(String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message);
    }

    // Un avertissement qui porte sur le processeur lui-même, et non sur le code examiné : il
    // échappe donc à -Aumlg.warnings=none.
    //
    // Faire taire les avertissements veut dire « ne me dis pas ce que tu as trouvé dans mon
    // code » ; cela ne peut pas vouloir dire « ne me dis pas que tu es mal configuré ». Un
    // fichier de règles introuvable désarme la moitié des contrôles : le taire ferait passer un
    // processeur aveugle pour un processeur satisfait, et le silence n'apprendrait plus rien.
    //
    // MANDATORY_WARNING, et non WARNING : c'est la seule gravité que -nowarn ne supprime pas.
    // Contourner -Aumlg.warnings=none sans elle ne servait à rien, puisque Maven passe -nowarn
    // par défaut — le cas courant était donc précisément le cas muet.
    void avertissementDeConfiguration(String message) {
        messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, message);
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

    // « La classe porteuse n'est pas sur le diagramme », l'erreur que neuf vérificateurs sur dix
    // avaient recopiée.
    //
    // Rien de ce qu'écrit la bibliothèque n'a de sens hors d'une boîte : une note s'accroche à
    // une boîte, une association part d'une boîte, une dérogation arbitre ce qu'une boîte montre.
    // La question est donc la même partout, et sa réponse aussi ; seule change la conséquence —
    // « la note n'aurait rien où s'accrocher », « le lien partirait de nulle part » —, qui reste
    // au site d'appel parce qu'elle seule dit à l'auteur ce qu'il perd.
    //
    // Rend vrai quand la boîte existe et que l'appelant peut poursuivre, faux quand l'erreur
    // vient d'être signalée.
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

    // Le contexte d'une annotation répétée, son rang écrit dans le texte.
    //
    // Une annotation répétée perd sa position : javac les emballe dans un conteneur synthétique,
    // qui n'existe nulle part dans le source et n'a donc aucune ligne à montrer. Le diagnostic se
    // pose alors sur la déclaration entière, et sur cinq @UMLNote dont une seule est fautive,
    // rien ne dit laquelle. L'API ne donne pas de quoi retrouver la place perdue — le texte est
    // le seul endroit qui reste pour la dire.
    //
    // Le rang n'est écrit que lorsqu'il y en a plusieurs : sur une annotation seule, javac sait
    // parfaitement la situer, et « the 1st » ne serait que du bruit.
    static String contexteDeLaRepetee(String nomAnnotation, String emplacement, int rang,
        int total) {
        String annotation = total == 1
            ? "@" + nomAnnotation
            : "the " + ordinal(rang + 1) + " @" + nomAnnotation;
        return annotation + " on " + emplacement;
    }

    // « 1st », « 2nd », « 3rd », « 4th »… La règle anglaise, y compris ses trois exceptions de
    // l'adolescence : 11th, 12th et 13th, et non 11st, 12nd, 13rd.
    private static String ordinal(int rang) {
        String suffixe = switch (rang % 100 >= 11 && rang % 100 <= 13 ? 0 : rang % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return rang + suffixe;
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

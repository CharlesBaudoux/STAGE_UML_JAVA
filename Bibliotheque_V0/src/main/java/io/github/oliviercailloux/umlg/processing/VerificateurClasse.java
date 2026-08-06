package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

// Vérifie ce que @UMLClass déclare : le nom, le stéréotype, la couleur.
final class VerificateurClasse {

    private final Rapporteur rapporteur;

    // Nom affiché -> nom complet de la première classe qui l'a pris. Sert à repérer deux boîtes
    // qui porteraient la même étiquette. Cet état survit d'un tour de compilation à l'autre,
    // puisque le processeur n'est construit qu'une fois.
    private final Map<String, String> premiereBoite = new HashMap<>();

    VerificateurClasse(Rapporteur rapporteur) {
        this.rapporteur = rapporteur;
    }

    void verifier(TypeElement classe) {
        AnnotationMirror annotation = rapporteur.miroir(classe, UMLClass.class);
        if (annotation == null) {
            return;
        }
        String contexte = "@UMLClass on " + classe.getQualifiedName();

        // La chaîne vide est le défaut, elle veut dire « rien de particulier ». Une chaîne
        // d'espaces, elle, est un oubli : elle produirait une boîte au nom invisible.
        verifierNonBlanc(classe, annotation, "name", contexte,
            "name() cannot be made of whitespace only.");
        verifierNonBlanc(classe, annotation, "stereotype", contexte,
            "stereotype() cannot be made of whitespace only.");

        verifierCouleur(classe, annotation, contexte);
        signalerStereotypeDejaEncadre(classe, annotation, contexte);
        signalerNomEnDouble(classe, annotation, contexte);
    }

    private void verifierNonBlanc(Element porteur, AnnotationMirror annotation, String membre,
        String contexte, String probleme) {
        String valeur = rapporteur.texte(annotation, membre);
        if (!valeur.isEmpty() && valeur.isBlank()) {
            rapporteur.erreur(contexte + ": " + probleme, porteur, annotation,
                rapporteur.valeurEcrite(annotation, membre));
        }
    }

    // La couleur est confiée à ColorCodec, celui-là même qu'emploie le rendu : la question posée
    // est donc la vraie — « PlantUML connaît-il cette couleur ? » — et non une approximation
    // syntaxique. C'est ce qui permet de refuser « PasUneCouleur » dès la compilation, alors que
    // sa forme, elle, est parfaitement valide.
    private void verifierCouleur(Element porteur, AnnotationMirror annotation, String contexte) {
        String couleur = rapporteur.texte(annotation, "color");
        if (couleur.isEmpty()) {
            return;
        }
        try {
            ColorCodec.code(couleur, porteur.toString());
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(contexte + ": " + e.getMessage(), porteur, annotation,
                rapporteur.valeurEcrite(annotation, "color"));
        }
    }

    // Les chevrons du stéréotype sont posés par le générateur : les écrire soi-même les
    // doublerait.
    //
    // La confusion est facile — un stéréotype UML s'écrit « <<service>> » partout ailleurs — et
    // sa conséquence est silencieuse : le rendu échappe les chevrons de l'utilisateur, si bien
    // que la boîte affiche « <<<<service>>>> » sans que rien n'ait échoué.
    private void signalerStereotypeDejaEncadre(Element porteur, AnnotationMirror annotation,
        String contexte) {
        String stereotype = rapporteur.texte(annotation, "stereotype");
        if (!stereotype.contains("<<") && !stereotype.contains(">>")) {
            return;
        }
        rapporteur.avertissement(contexte + ": stereotype() = \"" + stereotype + "\" already "
            + "contains the guillemets, which the generator adds itself; the box would show them "
            + "twice. Write the bare word.", porteur, annotation,
            rapporteur.valeurEcrite(annotation, "stereotype"));
    }

    // Deux boîtes portant la même étiquette.
    //
    // Ce n'est pas une erreur : deux classes de même nom simple dans deux paquetages sont
    // parfaitement légitimes, et le diagramme les distingue par leur nom complet — les alias du
    // rendu resteront d'ailleurs distincts. Mais le lecteur du dessin, lui, ne verra que deux
    // rectangles identiques. name() est là pour les départager.
    private void signalerNomEnDouble(TypeElement classe, AnnotationMirror annotation,
        String contexte) {
        String declare = rapporteur.texte(annotation, "name");
        String affiche = declare.isEmpty() ? classe.getSimpleName().toString() : declare;
        String qualifie = classe.getQualifiedName().toString();

        String precedent = premiereBoite.putIfAbsent(affiche, qualifie);
        // Null : le nom est libre. Égal au nôtre : la même classe revue à un tour suivant.
        if (precedent == null || precedent.equals(qualifie)) {
            return;
        }
        rapporteur.avertissement(contexte + ": the box would be labelled \"" + affiche
            + "\", exactly like " + precedent + ". The two are distinct classes, so the diagram "
            + "will show two boxes the reader cannot tell apart; set name() on one of them.",
            classe, annotation, rapporteur.valeurEcrite(annotation, "name"));
    }
}

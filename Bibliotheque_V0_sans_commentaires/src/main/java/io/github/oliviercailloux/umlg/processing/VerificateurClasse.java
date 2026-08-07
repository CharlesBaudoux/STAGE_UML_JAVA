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

    private void signalerNomEnDouble(TypeElement classe, AnnotationMirror annotation,
        String contexte) {
        String declare = rapporteur.texte(annotation, "name");
        String affiche = declare.isEmpty() ? classe.getSimpleName().toString() : declare;
        String qualifie = classe.getQualifiedName().toString();

        String precedent = premiereBoite.putIfAbsent(affiche, qualifie);
        if (precedent == null || precedent.equals(qualifie)) {
            return;
        }
        rapporteur.avertissement(contexte + ": the box would be labelled \"" + affiche
            + "\", exactly like " + precedent + ". The two are distinct classes, so the diagram "
            + "will show two boxes the reader cannot tell apart; set name() on one of them.",
            classe, annotation, rapporteur.valeurEcrite(annotation, "name"));
    }
}

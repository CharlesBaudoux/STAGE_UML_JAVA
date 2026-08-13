package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.extraction.PackageExtractor;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

final class VerificateurPackage {

    private final Rapporteur rapporteur;

    private final Map<String, Couleur> premiereCouleur = new HashMap<>();

    private record Couleur(String valeur, String classe) {}

    VerificateurPackage(Rapporteur rapporteur) {
        this.rapporteur = rapporteur;
    }

    void verifier(TypeElement classe) {
        AnnotationMirror annotation = rapporteur.miroir(classe, UMLPackage.class);
        if (annotation == null) {
            return;
        }
        String contexte = "@UMLPackage on " + classe.getQualifiedName();

        if (!rapporteur.exigerBoite(classe, contexte, "there is no box to group", classe,
            annotation)) {
            return;
        }

        String nom = rapporteur.texte(annotation, "name");
        if (nom.isBlank()) {
            rapporteur.erreur(PackageExtractor.messageNomDeGroupeVide(contexte), classe,
                annotation, rapporteur.valeurEcrite(annotation, "name"));
            return;
        }

        if (!verifierCouleur(classe, annotation, contexte)) {
            return;
        }
        signalerCouleurEnDesaccord(classe, annotation, contexte, nom);
    }

    private boolean verifierCouleur(Element porteur, AnnotationMirror annotation, String contexte) {
        String couleur = rapporteur.texte(annotation, "color");
        if (couleur.isEmpty()) {
            return true;
        }
        try {
            ColorCodec.code(couleur, porteur.toString());
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(contexte + ": " + e.getMessage(), porteur, annotation,
                rapporteur.valeurEcrite(annotation, "color"));
            return false;
        }
        return true;
    }

    private void signalerCouleurEnDesaccord(TypeElement classe, AnnotationMirror annotation,
        String contexte, String nom) {
        String couleur = rapporteur.texte(annotation, "color");
        if (couleur.isEmpty()) {
            return;
        }
        String qualifie = classe.getQualifiedName().toString();

        Couleur precedente = premiereCouleur.putIfAbsent(nom, new Couleur(couleur, qualifie));
        if (precedente == null || precedente.classe().equals(qualifie)
            || precedente.valeur().equals(couleur)) {
            return;
        }
        rapporteur.avertissement(contexte + ": group \"" + nom + "\" is already coloured \""
            + precedente.valeur() + "\" by " + precedente.classe() + ". A rectangle has only "
            + "one colour, so the first one wins and this one will not be drawn.",
            classe, annotation, rapporteur.valeurEcrite(annotation, "color"));
    }
}

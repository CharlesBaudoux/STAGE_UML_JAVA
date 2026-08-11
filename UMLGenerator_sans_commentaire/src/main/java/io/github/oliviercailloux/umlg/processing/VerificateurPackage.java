package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
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

        if (classe.getAnnotation(UMLClass.class) == null) {
            rapporteur.erreur(contexte + ": the declaring class does not carry @UMLClass, so it "
                + "is not part of the diagram and there is no box to group. Annotate it, or "
                + "remove this annotation.", classe, annotation);
            return;
        }

        String nom = rapporteur.texte(annotation, "name");
        if (nom.isBlank()) {
            rapporteur.erreur(contexte + ": name() cannot be empty or made of whitespace only; "
                + "a group is identified by its name.", classe, annotation,
                rapporteur.valeurEcrite(annotation, "name"));
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

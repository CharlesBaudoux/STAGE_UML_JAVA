package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

// Vérifie l'@UMLPackage posée sur une classe : le nom, la couleur, et l'accord entre classes
// d'un même groupe.
final class VerificateurPackage {

    private final Rapporteur rapporteur;

    // Nom de groupe -> la couleur retenue et la classe qui l'a fixée. Cet état survit d'un tour
    // de compilation à l'autre, le processeur n'étant construit qu'une fois : c'est ce qui permet
    // de comparer deux classes que rien d'autre ne rapproche.
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
            // name() n'a pas de défaut, javac exige donc qu'il soit écrit ; reste ce qu'il ne
            // voit pas, un nom vide ou fait d'espaces.
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

    // La couleur est confiée à ColorCodec, celui-là même qu'emploie le rendu : la question posée
    // est donc la vraie — « PlantUML connaît-il cette couleur ? » — et non une approximation.
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

    // Deux classes du même groupe qui déclarent deux couleurs.
    //
    // Ce n'est pas une erreur : le diagramme reste juste, et le groupe se dessine. Mais un
    // rectangle n'a qu'une couleur, si bien que l'une des deux ne sera pas peinte — et rien sur
    // le dessin ne dirait laquelle, ni pourquoi.
    private void signalerCouleurEnDesaccord(TypeElement classe, AnnotationMirror annotation,
        String contexte, String nom) {
        String couleur = rapporteur.texte(annotation, "color");
        // Une classe qui ne demande aucune couleur n'en perd aucune : elle n'a rien à dire sur
        // celle du rectangle, et ne doit donc ni la revendiquer ni être signalée.
        if (couleur.isEmpty()) {
            return;
        }
        String qualifie = classe.getQualifiedName().toString();

        Couleur precedente = premiereCouleur.putIfAbsent(nom, new Couleur(couleur, qualifie));
        // Null : personne n'avait encore coloré ce groupe. Même classe : revue à un tour
        // suivant. Même couleur : les deux classes sont d'accord, ce qui est le cas courant.
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

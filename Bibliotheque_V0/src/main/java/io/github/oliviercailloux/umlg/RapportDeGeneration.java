package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

// Ce que la génération a fait, dit en une fois.
//
// POURQUOI CELA REMPLACE UNE LIGNE PAR FICHIER
//
// Écrire « Success » à chaque image était juste tant qu'il n'y en avait qu'une. Douze vues
// produisaient douze lignes presque identiques, dans lesquelles la seule information utile — où
// est-ce, et qu'est-ce qui a été lu — se perdait.
//
// CE QU'IL DIT ET QUE PERSONNE NE SAVAIT
//
// Le nombre de classes ignorées. Une classe sans @UMLClass est hors du diagramme, et c'est
// voulu : le périmètre est choisi. Mais rien ne le disait, et sur un projet réel de trente et une
// classes, quinze manquaient au dessin sans que leur auteur ait moyen de s'en apercevoir
// autrement qu'en les comptant à la main. Une classe oubliée et une classe volontairement écartée
// se ressemblent trait pour trait ; seul le compte permet de faire la différence.
public record RapportDeGeneration(int classesLues, ImmutableList<String> classesIgnorees,
    ImmutableList<Path> fichiers) {

    public RapportDeGeneration {
        checkNotNull(classesIgnorees);
        checkNotNull(fichiers);
    }

    // Le compte rendu tel qu'il s'affiche, en deux ou trois lignes.
    //
    // Les classes ignorées ne sont énumérées que si elles sont peu nombreuses : au-delà, la liste
    // noierait le reste, et le nombre suffit à savoir qu'il faut aller voir.
    public String resume() {
        StringBuilder texte = new StringBuilder();
        texte.append(fichiers.size()).append(" diagram(s) written");
        if (!fichiers.isEmpty()) {
            texte.append(" to ").append(fichiers.get(0).getParent());
        }
        texte.append('\n').append("  ").append(classesLues)
             .append(" class(es) drawn, ").append(classesIgnorees.size())
             .append(" ignored (no @UMLClass)");

        if (!classesIgnorees.isEmpty() && classesIgnorees.size() <= SEUIL_ENUMERATION) {
            texte.append(" : ").append(String.join(", ", classesIgnorees));
        }
        return texte.toString();
    }

    // Au-delà de ce nombre, les classes ignorées sont comptées mais pas nommées.
    private static final int SEUIL_ENUMERATION = 12;
}

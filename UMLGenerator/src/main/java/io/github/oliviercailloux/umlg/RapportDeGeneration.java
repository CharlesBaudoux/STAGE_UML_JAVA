package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/**
 * Ce que la génération a fait, dit en une fois.
 *
 * <p>POURQUOI CELA REMPLACE UNE LIGNE PAR FICHIER
 *
 * <p>Écrire « Success » à chaque image était juste tant qu'il n'y en avait qu'une. Douze vues
 * produisaient douze lignes presque identiques, dans lesquelles la seule information utile — où
 * est-ce, et qu'est-ce qui a été lu — se perdait.
 *
 * <p>CE QU'IL DIT ET QUE PERSONNE NE SAVAIT
 *
 * <p>Le nombre de classes ignorées. Une classe sans @UMLClass est hors du diagramme, et c'est
 * voulu : le périmètre est choisi. Mais rien ne le disait, et sur un projet réel de trente et une
 * classes, quinze manquaient au dessin sans que leur auteur ait moyen de s'en apercevoir
 * autrement qu'en les comptant à la main. Une classe oubliée et une classe volontairement écartée
 * se ressemblent trait pour trait ; seul le compte permet de faire la différence.
 *
 * @param classesLues le nombre de classes portant {@code @UMLClass}, donc dessinées.
 * @param classesIgnorees les noms complets des classes rencontrées sans {@code @UMLClass}.
 * @param fichiers les images écrites, dans l'ordre où elles l'ont été.
 * @param fichierDesIgnorees le fichier qui énumère les classes ignorées, ou {@code null} quand il
 *     n'y avait rien à énumérer ou que la sortie n'a pas de dossier où le poser.
 */
public record RapportDeGeneration(int classesLues, ImmutableList<String> classesIgnorees,
    ImmutableList<Path> fichiers, Path fichierDesIgnorees) {

    /**
     * Vérifie les invariants du compte rendu.
     *
     * @throws NullPointerException si la liste des classes ignorées ou celle des fichiers est
     *     nulle. {@code fichierDesIgnorees}, lui, peut être {@code null} : c'est une absence
     *     légitime, pas un oubli.
     */
    public RapportDeGeneration {
        checkNotNull(classesIgnorees);
        checkNotNull(fichiers);
        // fichierDesIgnorees est null quand il n'y avait rien à énumérer, ou que la sortie n'a
        // pas de dossier où le poser : c'est une absence légitime, pas un oubli.
    }

    /**
     * Le même compte rendu, sans fichier de classes ignorées.
     *
     * @param classesLues le nombre de classes dessinées.
     * @param classesIgnorees les noms des classes rencontrées sans {@code @UMLClass}.
     * @param fichiers les images écrites.
     */
    public RapportDeGeneration(int classesLues, ImmutableList<String> classesIgnorees,
        ImmutableList<Path> fichiers) {
        this(classesLues, classesIgnorees, fichiers, null);
    }

    /**
     * Le compte rendu tel qu'il s'affiche, en deux ou trois lignes.
     *
     * <p>Les classes ignorées ne sont énumérées que si elles sont peu nombreuses : au-delà, la liste
     * noierait le reste, et le nombre suffit à savoir qu'il faut aller voir.
     *
     * @return le texte à afficher, sans saut de ligne final.
     */
    public String resume() {
        StringBuilder texte = new StringBuilder();
        texte.append(fichiers.size()).append(" diagram(s) written");
        if (!fichiers.isEmpty()) {
            // Un seul fichier se nomme ; plusieurs se résument par le dossier qui les tient.
            //
            // Nommer le dossier dans les deux cas taisait ce que l'utilisateur venait justement
            // de demander : « written to docs » pour « docs/archi.svg », et « written to . » dès
            // que le chemin commençait par « ./ ». Le null d'un chemin d'un seul segment était
            // rattrapé — c'était le fichier par défaut, et le message disait « written to
            // null » — mais le rattrapage traitait le symptôme, pas la question posée.
            Path cible = fichiers.size() == 1 ? fichiers.get(0) : fichiers.get(0).getParent();
            texte.append(" to ").append(cible == null ? fichiers.get(0) : cible);
        }
        texte.append('\n').append("  ").append(classesLues)
             .append(" class(es) drawn, ").append(classesIgnorees.size())
             .append(" ignored (no @UMLClass)");

        if (!classesIgnorees.isEmpty() && classesIgnorees.size() <= SEUIL_ENUMERATION) {
            texte.append(" : ").append(String.join(", ", classesIgnorees));
        }

        // Cette ligne était écrite à part, directement sur la sortie standard, et n'appartenait
        // donc pas au compte rendu : un appelant qui le relayait ailleurs — le plugin Maven dans
        // son log — la perdait en chemin, et l'utilisateur d'un build ne savait pas où trouver la
        // liste. Ce que le rapport dit doit tenir tout entier dans le rapport.
        if (fichierDesIgnorees != null) {
            texte.append('\n').append("  the full list of ignored classes is in ")
                 .append(fichierDesIgnorees);
        }
        return texte.toString();
    }

    /**
     * Au-delà de ce nombre, les classes ignorées sont comptées mais pas nommées.
     */
    private static final int SEUIL_ENUMERATION = 12;
}

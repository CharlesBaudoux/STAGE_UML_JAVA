package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;

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
 * <p>CE QU'IL DIT AUSSI, ET QU'IL FALLAIT AUTREFOIS ALLER CHERCHER
 *
 * <p>Les avertissements. Ils portent l'information la plus actionnable de l'outil — une vue vide,
 * une règle restée lettre morte, des classes illisibles — et ils n'étaient nulle part dans le
 * compte rendu : pour les capter, l'appelant devait encadrer son appel d'une pose et d'une dépose
 * de canal, un état global par fil dont il pouvait oublier la seconde moitié. Ils sont ici, et
 * {@code if (rapport.avertissements().isEmpty())} s'écrit enfin.
 *
 * @param classesLues le nombre de classes portant {@code @UMLClass}, donc dessinées.
 * @param classesIgnorees les noms complets des classes rencontrées sans {@code @UMLClass}.
 * @param fichiers les images écrites, dans l'ordre où elles l'ont été.
 * @param fichierDesIgnorees le fichier qui énumère les classes ignorées, vide quand il n'y avait
 *     rien à énumérer ou que la sortie n'a pas de dossier où le poser.
 * @param avertissements ce que la génération a eu à signaler, dans l'ordre où elle l'a dit ; vide
 *     quand elle n'a rien eu à dire.
 */
public record RapportDeGeneration(int classesLues, ImmutableList<String> classesIgnorees,
    ImmutableList<Path> fichiers, Optional<Path> fichierDesIgnorees,
    ImmutableList<String> avertissements) {

    /**
     * Vérifie les invariants du compte rendu.
     *
     * @throws NullPointerException si l'une des composantes est nulle. L'absence de fichier des
     *     ignorées se dit avec un {@link Optional} vide, et non avec {@code null} : c'était le
     *     seul {@code null} de toute la zone exportée, et rien dans la signature ne prévenait
     *     l'appelant qu'il devait s'y attendre.
     */
    public RapportDeGeneration {
        checkNotNull(classesIgnorees);
        checkNotNull(fichiers);
        checkNotNull(fichierDesIgnorees,
            "The ignored-classes file cannot be null (use Optional.empty()).");
        checkNotNull(avertissements, "The list of warnings cannot be null (use an empty list).");
    }

    /**
     * Le même compte rendu, sans fichier de classes ignorées ni avertissement.
     *
     * @param classesLues le nombre de classes dessinées.
     * @param classesIgnorees les noms des classes rencontrées sans {@code @UMLClass}.
     * @param fichiers les images écrites.
     */
    public RapportDeGeneration(int classesLues, ImmutableList<String> classesIgnorees,
        ImmutableList<Path> fichiers) {
        this(classesLues, classesIgnorees, fichiers, Optional.empty(), ImmutableList.of());
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
        if (fichierDesIgnorees.isPresent()) {
            texte.append('\n').append("  the full list of ignored classes is in ")
                 .append(fichierDesIgnorees.get());
        }
        return texte.toString();
    }

    /**
     * Au-delà de ce nombre, les classes ignorées sont comptées mais pas nommées.
     */
    private static final int SEUIL_ENUMERATION = 12;
}

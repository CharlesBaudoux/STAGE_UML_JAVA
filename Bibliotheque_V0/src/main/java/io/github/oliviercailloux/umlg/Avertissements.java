package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Consumer;

/**
 * Où vont les avertissements de la génération.
 *
 * <p>CE QUE CELA RÉPARE
 *
 * <p>Ils partaient tous sur la sortie d'erreur, écrits directement là où ils naissent — dans le
 * scan, dans le contrôle des proportions, dans la construction des vues. Lancée par Maven, la
 * génération les y écrivait donc telle quelle : sans préfixe {@code [WARNING]}, hors du log, et
 * absents du récapitulatif que Maven affiche en fin de construction. Le message le plus utile de
 * l'outil était celui qu'on voyait le moins.
 *
 * <p>Choisir le canal n'appartient pas à la bibliothèque, pas plus pour les avertissements que
 * pour le compte rendu. Elle dit ce qu'elle a à dire ; l'appelant décide où cela va.
 *
 * <p>LE FIL PLUTÔT QU'UN CHAMP GLOBAL
 *
 * <p>Le canal est propre au fil d'exécution, comme la liste des classes ignorées : deux
 * générations concurrentes — deux modules d'un réacteur Maven construit en parallèle — n'ont
 * aucune raison de partager leur destination. Le défaut reste la sortie d'erreur, qui est le bon
 * comportement d'un programme qu'on lance à la main.
 */
public final class Avertissements {

    /** Le canal par défaut : la sortie d'erreur, préfixée comme elle l'a toujours été. */
    private static final Consumer<String> SORTIE_DERREUR =
        message -> System.err.println("Warning: " + message);

    private static final ThreadLocal<Consumer<String>> CANAL =
        ThreadLocal.withInitial(() -> SORTIE_DERREUR);

    private Avertissements() {}

    /**
     * Dit un avertissement, sans le préfixe : le canal le pose s'il en veut un.
     *
     * @param message ce qu'il y a à savoir, et ce qu'on peut y faire
     */
    public static void dire(String message) {
        checkNotNull(message);
        CANAL.get().accept(message);
    }

    /**
     * Envoie les avertissements de ce fil ailleurs que sur la sortie d'erreur.
     *
     * <p>À rétablir par {@link #retablirLeDefaut()}, dans un {@code finally} : un canal laissé en
     * place survivrait à la génération et capterait celle d'après, sur un fil qu'un pool réutilise.
     *
     * @param canal où écrire désormais
     */
    public static void versLeCanal(Consumer<String> canal) {
        checkNotNull(canal);
        CANAL.set(canal);
    }

    /** Rend au fil courant son canal par défaut, la sortie d'erreur. */
    public static void retablirLeDefaut() {
        CANAL.remove();
    }
}

package io.github.oliviercailloux.umlg;

import io.github.oliviercailloux.umlg.warning.Avertisseur;
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
 * <p>CE QUE L'APPELANT N'A PLUS À FAIRE
 *
 * <p>Poser un canal ne sert plus qu'à <b>relayer</b> les avertissements au fil de l'eau, ce dont
 * un plugin Maven a besoin pour les écrire dans son log à mesure qu'ils arrivent. Pour simplement
 * les <b>lire</b> après coup, il n'y a rien à poser : {@link RapportDeGeneration#avertissements()}
 * les porte tous, et {@link UMLGenerator#generate()} se charge lui-même de les capter. L'appelant
 * qui encadrait son appel d'une pose et d'une dépose n'avait aucun moyen de savoir qu'il oubliait
 * la seconde.
 *
 * <p>LE FIL PLUTÔT QU'UN CHAMP GLOBAL
 *
 * <p>Le canal est propre au fil d'exécution, comme la liste des classes ignorées : deux
 * générations concurrentes — deux modules d'un réacteur Maven construit en parallèle — n'ont
 * aucune raison de partager leur destination. Le défaut reste la sortie d'erreur, qui est le bon
 * comportement d'un programme qu'on lance à la main.
 *
 * <p>CE QUI N'EST PLUS ICI
 *
 * <p>Le mécanisme lui-même — le canal du fil, et le geste de dire un avertissement — vit dans un
 * paquetage que le module n'exporte pas. Dire un avertissement est l'affaire de l'extraction et
 * du rendu ; l'utilisateur n'a aucune raison de le faire, et n'en a désormais plus le moyen.
 * Cette classe ne garde que la moitié qui le regarde : détourner les avertissements d'un fil, et
 * refermer.
 */
public final class Avertissements {

    private Avertissements() {}

    /**
     * Un canal posé, et de quoi le retirer.
     *
     * <p>À employer dans un {@code try}-avec-ressources : un canal laissé en place survivrait à la
     * génération et capterait celle d'après, sur un fil qu'un pool réutilise. C'était jusqu'ici à
     * l'appelant d'y penser, dans un {@code finally} qu'il pouvait oublier sans que rien ne le lui
     * dise.
     */
    public interface Canal extends AutoCloseable {

        /**
         * Rend au fil courant le canal qui était en place avant celui-ci.
         *
         * <p>Ne lève rien, à la différence d'{@link AutoCloseable#close()} : rendre son canal à un
         * fil ne peut pas échouer, et l'exception vérifiée obligerait tout appelant à l'attraper
         * pour rien.
         */
        @Override
        void close();
    }

    /**
     * Envoie les avertissements de ce fil ailleurs que sur la sortie d'erreur.
     *
     * <p>C'est le relais temps réel : le plugin Maven l'emploie pour que chaque avertissement
     * paraisse dans son log à l'instant où il naît, plutôt qu'en bloc à la fin. Qui se contente de
     * les relire après coup n'a rien à poser — voir {@link RapportDeGeneration#avertissements()}.
     *
     * @param canal où écrire désormais.
     * @return de quoi rendre au fil le canal qui était en place avant celui-ci ; à fermer dans un
     *     {@code try}-avec-ressources.
     */
    public static Canal redirectTo(Consumer<String> canal) {
        return Avertisseur.versLeCanal(canal);
    }
}

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
 */
public final class Avertissements {

    /** Le canal par défaut : la sortie d'erreur, préfixée comme elle l'a toujours été. */
    private static final Consumer<String> SORTIE_DERREUR =
        message -> System.err.println("Warning: " + message);

    // Vide tant que personne n'a rien détourné, et c'est voulu : « null » veut dire « la sortie
    // d'erreur », ce qui permet à la fermeture d'un canal de rendre au fil son état d'origine —
    // l'entrée retirée, et non réécrite avec la valeur par défaut. Un ThreadLocal.withInitial ne
    // distinguerait pas « jamais posé » de « posé au défaut », et laisserait une entrée derrière
    // lui sur chaque fil d'un pool.
    private static final ThreadLocal<Consumer<String>> CANAL = new ThreadLocal<>();

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
     * Dit un avertissement, sans le préfixe : le canal le pose s'il en veut un.
     *
     * <p><b>Usage interne.</b> Ce n'est pas une méthode de la surface publique : elle appartient
     * aux étages de l'extraction et du rendu, qui sont les seuls à avoir quelque chose à
     * avertir. Elle ne peut pas être rendue moins visible — les paquetages qui l'appellent ne
     * sont pas celui-ci, et Java n'accorde aucun privilège d'accès à l'intérieur d'un module —,
     * d'où le {@code @hidden} qui la retire au moins de la documentation publiée.
     *
     * @param message ce qu'il y a à savoir, et ce qu'on peut y faire
     * @hidden
     */
    public static void dire(String message) {
        checkNotNull(message);
        canalCourant().accept(message);
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
    public static Canal versLeCanal(Consumer<String> canal) {
        checkNotNull(canal, "The channel cannot be null.");
        return poser(canal);
    }

    // Ajoute un lecteur au canal en place, sans le remplacer.
    //
    // C'est ce dont generate() a besoin : les avertissements doivent entrer dans le compte rendu
    // qu'il rend, et continuer d'atteindre le canal que l'appelant avait posé — le log de Maven,
    // par exemple. Remplacer purement et simplement ferait taire le relais temps réel pendant
    // toute la génération, c'est-à-dire exactement pendant le moment où il sert.
    static Canal enPlusDuCanalCourant(Consumer<String> lecteur) {
        checkNotNull(lecteur);
        Consumer<String> enPlace = canalCourant();
        return poser(message -> {
            enPlace.accept(message);
            lecteur.accept(message);
        });
    }

    // Le canal du fil courant, la sortie d'erreur à défaut.
    private static Consumer<String> canalCourant() {
        Consumer<String> canal = CANAL.get();
        return canal == null ? SORTIE_DERREUR : canal;
    }

    // Pose un canal et rend de quoi restituer l'état exact d'avant : l'entrée retirée si le fil
    // n'en avait pas, l'ancien canal remis sinon. Les poses s'emboîtent donc, ce qui est
    // indispensable puisque generate() en ajoute une par-dessus celle du plugin Maven.
    private static Canal poser(Consumer<String> nouveau) {
        Consumer<String> precedent = CANAL.get();
        CANAL.set(nouveau);
        return () -> {
            if (precedent == null) {
                CANAL.remove();
            } else {
                CANAL.set(precedent);
            }
        };
    }
}

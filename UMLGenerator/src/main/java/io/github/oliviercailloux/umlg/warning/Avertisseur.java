package io.github.oliviercailloux.umlg.warning;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.Avertissements;
import java.util.function.Consumer;

// Le mécanisme des avertissements : le canal du fil courant, et ce qu'on y dit.
//
// POURQUOI CETTE CLASSE EXISTE, ET POURQUOI ELLE EST ICI
//
// « dire » n'a rien à faire dans la surface publique : elle appartient aux étages de l'extraction
// et du rendu, qui sont les seuls à avoir quelque chose à avertir. Elle vivait pourtant dans
// Avertissements, classe d'un paquetage exporté, si bien qu'un utilisateur pouvait l'appeler pour
// injecter ses propres messages dans le compte rendu de la bibliothèque. Le @hidden de la Javadoc
// la retirait de la documentation publiée, et c'était tout : le code compilait toujours.
//
// La déclarer dans une classe non exportée dont Avertissements hériterait ne fermait rien —
// mesuré : un membre public hérité par une classe publique d'un paquetage exporté reste
// accessible hors du module, à la compilation comme à l'exécution, l'appel se résolvant sur la
// classe qui hérite et non sur celle qui déclare. Seul le déplacement ferme la porte, et c'est
// ce que fait ce paquetage, qui ne figure pas dans les exports du module-info : à l'intérieur,
// « Avertisseur.dire(...) » compile ; à l'extérieur, l'import ne passe plus.
//
// Ce que la façade garde. Avertissements reste exportée, avec ce que l'appelant a de bonnes
// raisons d'employer : poser un canal, et le refermer. C'est la seule moitié qui le regarde.
//
// LE FIL PLUTÔT QU'UN CHAMP GLOBAL
//
// Le canal est propre au fil d'exécution, comme la liste des classes ignorées : deux générations
// concurrentes — deux modules d'un réacteur Maven construit en parallèle — n'ont aucune raison de
// partager leur destination. Le défaut reste la sortie d'erreur, qui est le bon comportement d'un
// programme qu'on lance à la main.
public final class Avertisseur {

    // Le canal par défaut : la sortie d'erreur, préfixée comme elle l'a toujours été.
    private static final Consumer<String> SORTIE_DERREUR =
        message -> System.err.println("Warning: " + message);

    // Vide tant que personne n'a rien détourné, et c'est voulu : « null » veut dire « la sortie
    // d'erreur », ce qui permet à la fermeture d'un canal de rendre au fil son état d'origine —
    // l'entrée retirée, et non réécrite avec la valeur par défaut. Un ThreadLocal.withInitial ne
    // distinguerait pas « jamais posé » de « posé au défaut », et laisserait une entrée derrière
    // lui sur chaque fil d'un pool.
    private static final ThreadLocal<Consumer<String>> CANAL = new ThreadLocal<>();

    private Avertisseur() {}

    // Dit un avertissement, sans le préfixe : le canal le pose s'il en veut un.
    public static void dire(String message) {
        checkNotNull(message);
        canalCourant().accept(message);
    }

    // Envoie les avertissements de ce fil ailleurs que sur la sortie d'erreur. C'est ce
    // qu'Avertissements.versLeCanal offre à l'appelant.
    public static Avertissements.Canal versLeCanal(Consumer<String> canal) {
        checkNotNull(canal, "The channel cannot be null.");
        return poser(canal);
    }

    // Ajoute un lecteur au canal en place, sans le remplacer.
    //
    // C'est ce dont generate() a besoin : les avertissements doivent entrer dans le compte rendu
    // qu'il rend, et continuer d'atteindre le canal que l'appelant avait posé — le log de Maven,
    // par exemple. Remplacer purement et simplement ferait taire le relais temps réel pendant
    // toute la génération, c'est-à-dire exactement pendant le moment où il sert.
    public static Avertissements.Canal enPlusDuCanalCourant(Consumer<String> lecteur) {
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
    private static Avertissements.Canal poser(Consumer<String> nouveau) {
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

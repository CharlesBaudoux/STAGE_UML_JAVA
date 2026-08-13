package io.github.oliviercailloux.umlg.warning;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.Avertissements;
import java.util.function.Consumer;

public final class Avertisseur {

    private static final Consumer<String> SORTIE_DERREUR =
        message -> System.err.println("Warning: " + message);

    private static final ThreadLocal<Consumer<String>> CANAL = new ThreadLocal<>();

    private Avertisseur() {}

    public static void dire(String message) {
        checkNotNull(message);
        canalCourant().accept(message);
    }

    public static Avertissements.Canal versLeCanal(Consumer<String> canal) {
        checkNotNull(canal, "The channel cannot be null.");
        return poser(canal);
    }

    public static Avertissements.Canal enPlusDuCanalCourant(Consumer<String> lecteur) {
        checkNotNull(lecteur);
        Consumer<String> enPlace = canalCourant();
        return poser(message -> {
            enPlace.accept(message);
            lecteur.accept(message);
        });
    }

    private static Consumer<String> canalCourant() {
        Consumer<String> canal = CANAL.get();
        return canal == null ? SORTIE_DERREUR : canal;
    }

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

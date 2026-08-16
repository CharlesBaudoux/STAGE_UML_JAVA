package io.github.oliviercailloux.umlg;

import io.github.oliviercailloux.umlg.warning.Warner;
import java.util.function.Consumer;

/**
 * Where the generation's warnings go: the library says what it has to say, and the caller
 * decides where it goes.
 */
public final class Warnings {
    private Warnings() {}

    /** The relay set by the caller, to be closed to return to the previous channel. */
    public interface Channel extends AutoCloseable {
        /** Restores the previous channel. */
        @Override
        void close();
    }

    /**
     * Relays warnings as they come, which a Maven plugin needs in order to
     * write them to its log as they come.
     *
     * @param channel where to carry each warning.
     * @return the relay, to be closed once it has served its purpose.
     */
    public static Channel redirectTo(Consumer<String> channel) {
        return Warner.toChannel(channel);
    }
}

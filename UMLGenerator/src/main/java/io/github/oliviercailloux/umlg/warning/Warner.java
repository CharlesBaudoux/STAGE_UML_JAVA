package io.github.oliviercailloux.umlg.warning;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.Warnings;
import java.util.function.Consumer;

public final class Warner {
    private static final Consumer<String> ERROR_OUTPUT =
        message -> System.err.println("Warning: " + message);

    private static final ThreadLocal<Consumer<String>> Channel = new ThreadLocal<>();

    private Warner() {}

    public static void say(String message) {
        checkNotNull(message);
        currentChannel().accept(message);
    }

    public static Warnings.Channel toChannel(Consumer<String> channel) {
        checkNotNull(channel, "The channel cannot be null.");
        return place(channel);
    }

    public static Warnings.Channel onTopOfTheCurrentChannel(Consumer<String> reader) {
        checkNotNull(reader);
        Consumer<String> enPlaced = currentChannel();
        return place(message -> {
            enPlaced.accept(message);
            reader.accept(message);
        });
    }

    private static Consumer<String> currentChannel() {
        Consumer<String> channel = Channel.get();
        return channel == null ? ERROR_OUTPUT : channel;
    }

    private static Warnings.Channel place(Consumer<String> fresh) {
        Consumer<String> previous = Channel.get();
        Channel.set(fresh);
        return () -> {
            if (previous == null) {
                Channel.remove();
            } else {
                Channel.set(previous);
            }
        };
    }
}

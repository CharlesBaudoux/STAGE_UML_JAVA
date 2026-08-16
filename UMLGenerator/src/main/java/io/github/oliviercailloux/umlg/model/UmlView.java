package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import java.text.Normalizer;
import java.util.Locale;

/**
 * A subset of the diagram, written in its own file.
 *
 * @param name the name of the view, used as its title and as its file name.
 * @param entities the boxes the view keeps.
 * @param direction the direction in which this view unfolds.
 */
public record UmlView(String name, ImmutableList<UmlEntity> entities, UMLFlow direction) {
    /** Checks that the view carries a name and keeps at least one box. */
    public UmlView {
        checkNotNull(name, "The view name cannot be null.");
        checkNotNull(entities, "The list of entities cannot be null.");
        checkNotNull(direction, "The direction cannot be null; use UMLFlow.TOP_TO_BOTTOM.");

        checkArgument(!name.isBlank(), "The view name cannot be empty or made of whitespace only.");

        checkArgument(!entities.isEmpty(), "View \"%s\" is empty; it would draw nothing.", name);
    }

    /** {@return the name of the file to write, drawn from the name of the view} */
    public String fileName() {
        String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        String replaced = withoutAccents.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-");
        String own = replaced.replaceAll("^-+|-+$", "");

        return own.isEmpty() ? "view" : own.toLowerCase(Locale.ROOT);
    }
}

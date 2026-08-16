package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The whole diagram: its boxes, its overall settings, and its views.
 *
 * @param entities the boxes, identified by their qualified name.
 * @param diagram the title, the reading direction, and the fate of empty compartments.
 * @param views the declared views, in the order of the files produced.
 */
public record UmlProject(ImmutableSet<UmlEntity> entities, UmlDiagram diagram,
    ImmutableList<UmlView> views) {
    /**
     * A project with default settings and no view.
     *
     * @param entities the boxes of the diagram.
     * @return the project, with default settings and no view.
     */
    public static UmlProject given(Set<UmlEntity> entities) {
        return given(entities, UmlDiagram.defaults());
    }

    /**
     * One project without no view.
     *
     * @param entities the boxes of the diagram.
     * @param diagram the overall settings.
     * @return the project, with no view.
     */
    public static UmlProject given(Set<UmlEntity> entities, UmlDiagram diagram) {
        return given(entities, diagram, List.of());
    }

    /**
     * A project, its overall settings and its views.
     *
     * @param entities the boxes of the diagram.
     * @param diagram the overall settings.
     * @param views the declared views.
     * @return the project.
     */
    public static UmlProject given(Set<UmlEntity> entities, UmlDiagram diagram,
        List<UmlView> views) {
        checkNotNull(entities, "The set of entities cannot be null.");
        return new UmlProject(ImmutableSet.copyOf(entities), diagram,
            ImmutableList.copyOf(checkNotNull(views, "The list of views cannot be null.")));
    }

    /**
     * Checks that there is at least one box, and that no two of them carry the same name
     * complete.
     */
    public UmlProject {
        checkNotNull(entities, "The set of entities cannot be null.");
        checkNotNull(diagram, "The diagram settings cannot be null; use UmlDiagram.defaults().");
        checkNotNull(views, "The list of views cannot be null (use an empty list).");

        checkArgument(!entities.isEmpty(),
            "The project must contain at least one annotated class to generate a diagram.");
        checkIdentity(entities);
    }

    private static void checkIdentity(ImmutableSet<UmlEntity> entities) {
        Set<String> seen = new HashSet<>();
        for (UmlEntity entity : entities) {
            checkArgument(seen.add(entity.qualifiedName()),
                "Two entities share the qualified name \"%s\", which is what identifies a box; "
                    + "the diagram would draw one of them and silently drop the other.",
                entity.qualifiedName());
        }
    }
}

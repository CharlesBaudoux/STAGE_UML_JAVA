package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.extraction.rules.ViewDefinition;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.extraction.rules.Selector;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.model.UmlView;
import io.github.oliviercailloux.umlg.warning.Warner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

public final class ViewBuilder {
    public static final String FULL_VIEW = "Project";

    static final String PACKAGE_BY_DEFAULT = "(default)";

    private static final int BLOCKS_CHAINABLE = 3;

    private ViewBuilder() {}

    public static ImmutableList<UmlView> build(ImmutableSet<UmlEntity> entities,
        UmlDiagram diagram, Rules rules) {
        checkNotNull(entities);
        checkNotNull(diagram);
        checkNotNull(rules);

        if (entities.isEmpty()) {
            return ImmutableList.of();
        }

        ImmutableList.Builder<UmlView> named = ImmutableList.builder();
        for (ViewDefinition definition : rules.views()) {
            named.add(declared(definition, entities));
        }
        if (rules.viewsByPackage()) {
            named.addAll(byPackage(entities));
        }
        ImmutableList<UmlView> views = named.build();
        verifierLesNomsDeFichier(views);

        String title = diagram.hasTitle() ? diagram.title() : FULL_VIEW;
        return ImmutableList.<UmlView>builder()
            .add(new UmlView(title, ImmutableList.copyOf(entities), diagram.direction()))
            .addAll(views)
            .build();
    }

    private static void verifierLesNomsDeFichier(ImmutableList<UmlView> views) {
        Map<String, String> byFile = new LinkedHashMap<>();
        for (UmlView view : views) {
            String file = view.fileName();
            String first = byFile.putIfAbsent(file, view.name());
            checkArgument(first == null,
                "Views \"%s\" and \"%s\" both come down to the file name \"%s\", so the second "
                    + "would overwrite the first. A file name is the view name without its "
                    + "accents, with everything that is neither a letter nor a digit turned into "
                    + "a dash, in lower case: give one of the two a name that differs by more "
                    + "than punctuation or case.",
                first, view.name(), file);
        }
    }

    private static UmlView declared(ViewDefinition definition, ImmutableSet<UmlEntity> entities) {
        Set<UmlEntity> kept = new LinkedHashSet<>();
        for (Selector selector : definition.kept()) {
            kept.addAll(resolve(selector, entities, definition.name()));
        }
        for (Selector selector : definition.removed()) {
            kept.removeAll(resolve(selector, entities, definition.name()));
        }
        checkArgument(!kept.isEmpty(),
            "View \"%s\" retains no class; everything it named was also removed.",
            definition.name());

        return new UmlView(definition.name(), ordered(kept), definition.direction());
    }

    private static ImmutableList<UmlView> byPackage(ImmutableSet<UmlEntity> entities) {
        Map<String, List<UmlEntity>> byName = new LinkedHashMap<>();
        for (UmlEntity entity : entities) {
            byName.computeIfAbsent(packageOf(entity), name -> new ArrayList<>()).add(entity);
        }
        ImmutableList.Builder<UmlView> views = ImmutableList.builder();
        for (String pkg : byName.keySet().stream().sorted().toList()) {
            views.add(new UmlView(pkg, ordered(byName.get(pkg)),
                UMLFlow.TOP_TO_BOTTOM));
        }
        return views.build();
    }

    private static String packageOf(UmlEntity entity) {
        String complete = entity.qualifiedName();
        int point = complete.lastIndexOf('.');
        return point < 0 ? PACKAGE_BY_DEFAULT : complete.substring(0, point);
    }

    private static ImmutableList<UmlEntity> resolve(Selector selector,
        ImmutableSet<UmlEntity> entities, String view) {
        ImmutableList<UmlEntity> found = switch (selector.kind()) {
            case Class -> byName(selector.value(), entities);
            case Package -> entities.stream()
                .filter(e -> packageOf(e).equals(selector.value()))
                .collect(ImmutableList.toImmutableList());
            case Neighborhood -> neighborhood(selector.value(), entities, view);
        };

        checkArgument(!found.isEmpty(),
            "View \"%s\": \"%s\" matches no class of the diagram. A class must carry @UMLClass to "
                + "be named here, and its name must be spelled as in the code.",
            view, selector.written());

        return found;
    }

    private static ImmutableList<UmlEntity> byName(String name, ImmutableSet<UmlEntity> entities) {
        return entities.stream()
            .filter(e -> e.qualifiedName().equals(name) || nameSimple(e).equals(name))
            .collect(ImmutableList.toImmutableList());
    }

    private static String nameSimple(UmlEntity entity) {
        String complete = entity.qualifiedName();

        int separator = Math.max(complete.lastIndexOf('.'), complete.lastIndexOf('$'));
        return separator < 0 ? complete : complete.substring(separator + 1);
    }

    private static ImmutableList<UmlEntity> neighborhood(String name, ImmutableSet<UmlEntity> entities,
        String view) {
        ImmutableList<UmlEntity> centered = byName(name, entities);
        checkArgument(!centered.isEmpty(),
            "View \"%s\": \"around %s\" matches no class of the diagram.", view, name);

        Set<String> wanted = new LinkedHashSet<>();
        for (UmlEntity center : centered) {
            wanted.add(center.qualifiedName());

            for (UmlRelation relation : center.relations()) {
                wanted.addAll(endpoints(relation));
            }
        }

        for (UmlEntity candidate : entities) {
            for (UmlRelation relation : candidate.relations()) {
                if (centered.stream()
                    .anyMatch(c -> endpoints(relation).contains(c.qualifiedName()))) {
                    wanted.add(candidate.qualifiedName());
                }
            }
        }
        reportEmptyNeighborhood(wanted, centered, name, view);

        return entities.stream().filter(e -> wanted.contains(e.qualifiedName()))
            .collect(ImmutableList.toImmutableList());
    }

    static boolean reportEmptyNeighborhood(Set<String> wanted, ImmutableList<UmlEntity> centered,
        String name, String view) {
        if (wanted.size() > centered.size()) {
            return false;
        }
        Warner.say("view \"" + view + "\": \"around " + name + "\" found no "
            + "neighbour, so the view shows the class alone. Neighbours come from declared "
            + "relations — @UMLAssociation, @UMLDependency, inheritance between annotated "
            + "classes — not from the types used by fields or methods. Declare a relation, or "
            + "name the classes of this view explicitly.");
        return true;
    }

    private static ImmutableSet<String> endpoints(UmlRelation relation) {
        if (relation instanceof UmlAssociationClass clazz) {
            return ImmutableSet.of(clazz.sourceQualifiedName(), clazz.targetQualifiedName());
        }
        return ImmutableSet.of(relation.targetQualifiedName());
    }

    private static ImmutableList<UmlEntity> ordered(Iterable<UmlEntity> kept) {
        ImmutableSet<String> present = ImmutableSet.copyOf(
            StreamSupport.stream(kept.spliterator(), false)
                .map(UmlEntity::qualifiedName).toList());

        List<UmlEntity> pruned = new ArrayList<>();
        for (UmlEntity entity : kept) {
            pruned.add(prune(entity, present));
        }
        return chain(pruned);
    }

    private static UmlEntity prune(UmlEntity entity, ImmutableSet<String> present) {
        return entity.withRelations(entity.relations().stream()
            .filter(relation -> present.containsAll(endpoints(relation)))
            .collect(ImmutableList.toImmutableList()));
    }

    private static ImmutableList<UmlEntity> chain(List<UmlEntity> entities) {
        if (entities.size() < 2) {
            return ImmutableList.copyOf(entities);
        }
        List<List<UmlEntity>> blocks = blocks(entities);
        if (blocks.size() < 2 || blocks.size() > BLOCKS_CHAINABLE) {
            return ImmutableList.copyOf(entities);
        }

        Map<String, String> toBlocNext = new LinkedHashMap<>();
        for (int i = 0; i + 1 < blocks.size(); i++) {
            toBlocNext.put(blocks.get(i).get(0).qualifiedName(),
                blocks.get(i + 1).get(0).qualifiedName());
        }

        ImmutableList.Builder<UmlEntity> result = ImmutableList.builder();
        for (UmlEntity entity : entities) {
            String next = toBlocNext.get(entity.qualifiedName());
            result.add(next == null ? entity : withOrder(entity, next));
        }
        return result.build();
    }

    private static UmlEntity withOrder(UmlEntity entity, String next) {
        return entity.withRelations(ImmutableList.<UmlRelation>builder()
            .addAll(entity.relations())
            .add(new UmlLayout(next, UMLDirection.NONE))
            .build());
    }

    private static List<List<UmlEntity>> blocks(List<UmlEntity> entities) {
        Map<String, Set<String>> neighbors = adjacence(entities);
        Map<String, UmlEntity> byName = new LinkedHashMap<>();
        for (UmlEntity entity : entities) {
            byName.put(entity.qualifiedName(), entity);
        }

        Set<String> views = new LinkedHashSet<>();
        List<List<UmlEntity>> blocks = new ArrayList<>();

        for (UmlEntity start : entities) {
            if (!views.add(start.qualifiedName())) {
                continue;
            }

            Set<String> bloc = new LinkedHashSet<>();
            bloc.add(start.qualifiedName());
            List<String> toSee = new ArrayList<>(List.of(start.qualifiedName()));

            while (!toSee.isEmpty()) {
                String current = toSee.remove(0);
                for (String neighbor : neighbors.getOrDefault(current, Set.of())) {
                    if (bloc.add(neighbor)) {
                        views.add(neighbor);
                        toSee.add(neighbor);
                    }
                }
            }
            blocks.add(entities.stream().filter(e -> bloc.contains(e.qualifiedName())).toList());
        }
        return blocks;
    }

    private static Map<String, Set<String>> adjacence(List<UmlEntity> entities) {
        Map<String, Set<String>> neighbors = new LinkedHashMap<>();
        for (UmlEntity entity : entities) {
            for (UmlRelation relation : entity.relations()) {
                for (String end : endpoints(relation)) {
                    neighbors.computeIfAbsent(entity.qualifiedName(), n -> new LinkedHashSet<>())
                        .add(end);
                    neighbors.computeIfAbsent(end, n -> new LinkedHashSet<>())
                        .add(entity.qualifiedName());
                }
            }
        }
        return neighbors;
    }
}

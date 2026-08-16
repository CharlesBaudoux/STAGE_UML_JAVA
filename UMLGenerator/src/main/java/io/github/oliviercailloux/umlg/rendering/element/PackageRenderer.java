package io.github.oliviercailloux.umlg.rendering.element;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import io.github.oliviercailloux.umlg.warning.Warner;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PackageRenderer {
    public static final String Closing = "}\n\n";

    private PackageRenderer() {}

    public static ImmutableList<Map.Entry<UmlPackage, ImmutableList<UmlEntity>>> groups(
        Iterable<UmlEntity> entities) {
        Map<String, UmlPackage> keptGroups = new LinkedHashMap<>();
        Map<String, ImmutableList.Builder<UmlEntity>> members = new LinkedHashMap<>();

        Map<String, String> firstColor = new LinkedHashMap<>();

        for (UmlEntity entity : entities) {
            UmlPackage group = entity.group();
            String key = group.name();

            UmlPackage kept = keptGroups.get(key);
            if (kept == null) {
                keptGroups.put(key, group);
                members.put(key, ImmutableList.builder());
                if (group.isGrouped() && !group.color().isEmpty()) {
                    firstColor.put(key, entity.qualifiedName());
                }
            } else if (group.isGrouped() && !group.color().isEmpty()
                && !group.color().equals(kept.color())) {
                if (kept.color().isEmpty()) {
                    keptGroups.put(key, group);
                    firstColor.put(key, entity.qualifiedName());
                } else {
                    reportDuplicateColor(entity, group, kept, firstColor.get(key));
                }
            }
            members.get(key).add(entity);
        }

        ImmutableList.Builder<Map.Entry<UmlPackage, ImmutableList<UmlEntity>>> result =
            ImmutableList.builder();
        for (Map.Entry<String, UmlPackage> group : keptGroups.entrySet()) {
            result.add(Map.entry(group.getValue(), members.get(group.getKey()).build()));
        }
        return result.build();
    }

    public static String opening(UmlPackage group) {
        StringBuilder line = new StringBuilder("package \"")
            .append(PlantUmlEscaper.lineStart(group.name())).append('"');

        if (!group.color().isEmpty()) {
            line.append(' ').append(ColorCodec.code(group.color(), group.name()));
        }
        return line.append(" {\n").toString();
    }

    private static void reportDuplicateColor(UmlEntity entity, UmlPackage declare,
        UmlPackage kept, String first) {
        Warner.say("@UMLPackage on " + entity.qualifiedName() + ": group \""
            + declare.name() + "\" already has the color \"" + kept.color() + "\""
            + (first == null ? "" : ", set by " + first)
            + "; a rectangle has only one, so \"" + declare.color() + "\" will not be drawn.");
    }
}

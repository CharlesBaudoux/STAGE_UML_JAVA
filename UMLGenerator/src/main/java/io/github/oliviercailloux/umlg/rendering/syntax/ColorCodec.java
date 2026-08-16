package io.github.oliviercailloux.umlg.rendering.syntax;

import com.google.common.collect.ImmutableSortedSet;
import net.sourceforge.plantuml.klimt.color.HColorSet;
import net.sourceforge.plantuml.klimt.color.NoSuchColorException;

public final class ColorCodec {
    private ColorCodec() {}

    public static String code(String color, String contextFaulty) {
        String code = color.startsWith("#") ? color : "#" + color;
        try {
            HColorSet.instance().getColor(code);
        } catch (NoSuchColorException e) {
            throw new IllegalArgumentException(
                "Color unknown to PlantUML for \"" + contextFaulty + "\": \""
                    + color + "\". Expected: a hexadecimal code (\"#FF0000\", \"#ABC\", "
                    + "\"#FF000080\" with transparency) or a PlantUML color name (\"red\", "
                    + "\"LightBlue\") — the full catalogue is given by "
                    + "UMLGenerator.knownColors().", e);
        }
        return code;
    }

    public static ImmutableSortedSet<String> namesKnown() {
        return ImmutableSortedSet.copyOf(String.CASE_INSENSITIVE_ORDER,
            HColorSet.instance().names());
    }
}

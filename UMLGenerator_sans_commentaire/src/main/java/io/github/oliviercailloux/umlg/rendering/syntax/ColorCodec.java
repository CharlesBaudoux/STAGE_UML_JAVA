package io.github.oliviercailloux.umlg.rendering.syntax;

import com.google.common.collect.ImmutableSortedSet;
import net.sourceforge.plantuml.klimt.color.HColorSet;
import net.sourceforge.plantuml.klimt.color.NoSuchColorException;

public final class ColorCodec {

    private ColorCodec() {}

    public static String code(String couleur, String contexteFautif) {
        String code = couleur.startsWith("#") ? couleur : "#" + couleur;
        try {
            HColorSet.instance().getColor(code);
        } catch (NoSuchColorException e) {
            throw new IllegalArgumentException(
                "Color unknown to PlantUML for \"" + contexteFautif + "\": \""
                    + couleur + "\". Expected: a hexadecimal code (\"#FF0000\", \"#ABC\", "
                    + "\"#FF000080\" with transparency) or a PlantUML color name (\"red\", "
                    + "\"LightBlue\") — the full catalogue is given by "
                    + "UMLGenerator.couleursConnues().", e);
        }
        return code;
    }

    public static ImmutableSortedSet<String> nomsConnus() {
        return ImmutableSortedSet.copyOf(String.CASE_INSENSITIVE_ORDER,
            HColorSet.instance().names());
    }
}

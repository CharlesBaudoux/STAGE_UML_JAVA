package io.github.oliviercailloux.umlg.rendering.syntax;

import net.sourceforge.plantuml.klimt.color.HColorSet;
import net.sourceforge.plantuml.klimt.color.NoSuchColorException;

// Traduit la couleur écrite dans @UMLClass en couleur que PlantUML acceptera.
//
// Complément de ClassHeaderValidator, et non doublon : celui-ci vérifie la forme (« ça
// ressemble à une couleur »), celui-là interroge le catalogue réel de PlantUML (« Turquoise
// existe-t-elle ? »). Seul le rendu peut poser la seconde question, puisque seul lui connaît
// le moteur de dessin.
public final class ColorCodec {

    private ColorCodec() {}

    public static String code(String couleur, String classeFautive) {
        String code = couleur.startsWith("#") ? couleur : "#" + couleur;
        try {
            HColorSet.instance().getColor(code);
        } catch (NoSuchColorException e) {
            throw new IllegalArgumentException(
                "Color unknown to PlantUML for class \"" + classeFautive + "\": \""
                    + couleur + "\". Expected: a hexadecimal code (\"#FF0000\", \"#ABC\") "
                    + "or a PlantUML color name (\"red\", \"LightBlue\").", e);
        }
        return code;
    }
}

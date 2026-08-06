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
        // PlantUML veut un '#' devant, aussi bien pour un code hexadécimal que pour un nom de
        // couleur. On l'ajoute si l'utilisateur ne l'a pas écrit.
        String code = couleur.startsWith("#") ? couleur : "#" + couleur;
        try {
            // Appel fait pour son effet de bord : la valeur rendue ne sert pas, seul compte le
            // fait que la couleur soit reconnue ou non.
            HColorSet.instance().getColor(code);
        } catch (NoSuchColorException e) {
            // Le nom de la classe est passé en paramètre justement pour ce message : sans lui,
            // l'utilisateur saurait qu'une couleur est fausse, mais pas laquelle corriger.
            throw new IllegalArgumentException(
                "Color unknown to PlantUML for class \"" + classeFautive + "\": \""
                    + couleur + "\". Expected: a hexadecimal code (\"#FF0000\", \"#ABC\") "
                    + "or a PlantUML color name (\"red\", \"LightBlue\").", e);
        }
        return code;
    }
}

package io.github.oliviercailloux.umlg.rendering.syntax;

import com.google.common.collect.ImmutableSortedSet;
import net.sourceforge.plantuml.klimt.color.HColorSet;
import net.sourceforge.plantuml.klimt.color.NoSuchColorException;

// Traduit la couleur écrite dans @UMLClass en couleur que PlantUML acceptera.
//
// LA SEULE GRAMMAIRE DE COULEUR DE LA BIBLIOTHÈQUE
//
// Il y en a eu deux : une expression régulière côté extraction, qui vérifiait la forme, et cet
// appel au catalogue de PlantUML côté rendu et côté processeur. Elles ne coïncidaient pas, et
// pas seulement à la marge : « #FF000080 » — un hexadécimal à huit chiffres, avec transparence —
// passait la compilation sans un mot, puis faisait échouer la génération. Un processeur
// d'annotations qui laisse passer ce qui plantera plus tard ne sert à rien.
//
// Cette classe est donc désormais l'unique juge, appelée par les trois : ClassHeaderValidator à
// l'extraction, VerificateurClasse et VerificateurPackage à la compilation, ClassBoxRenderer et
// PackageRenderer au dessin. Elle interroge le moteur lui-même, ce qui est la seule façon de ne
// pas se tromper sur ce qu'il accepte.
public final class ColorCodec {

    private ColorCodec() {}

    // Le contexte est repris tel quel dans le message : c'est lui qui nomme l'élément fautif —
    // « @UMLPackage on com.ex.Facture », « com.ex.Compte » —, sans quoi l'utilisateur saurait
    // qu'une couleur est fausse, mais pas laquelle corriger.
    public static String code(String couleur, String contexteFautif) {
        // PlantUML veut un '#' devant, aussi bien pour un code hexadécimal que pour un nom de
        // couleur. On l'ajoute si l'utilisateur ne l'a pas écrit.
        String code = couleur.startsWith("#") ? couleur : "#" + couleur;
        try {
            // Appel fait pour son effet de bord : la valeur rendue ne sert pas, seul compte le
            // fait que la couleur soit reconnue ou non.
            HColorSet.instance().getColor(code);
        } catch (NoSuchColorException e) {
            // Le catalogue est nommé, et non seulement décrit : la méthode qui l'énumère existe
            // précisément pour cette question, et le message était jusqu'ici le seul endroit d'où
            // elle n'était pas visible.
            throw new IllegalArgumentException(
                "Color unknown to PlantUML for \"" + contexteFautif + "\": \""
                    + couleur + "\". Expected: a hexadecimal code (\"#FF0000\", \"#ABC\", "
                    + "\"#FF000080\" with transparency) or a PlantUML color name (\"red\", "
                    + "\"LightBlue\") — the full catalogue is given by "
                    + "UMLGenerator.couleursConnues().", e);
        }
        return code;
    }

    // Les noms de couleurs que PlantUML connaît, triés sans égard à la casse.
    //
    // Ce catalogue était enfermé ici, dans un paquetage que le module n'exporte pas : un
    // utilisateur dont la couleur était refusée n'avait aucun moyen de demander lesquelles
    // existent. La façade l'expose (UMLGenerator.couleursConnues) ; cette méthode est la seule
    // à savoir le lire, puisqu'elle seule connaît le moteur de dessin.
    public static ImmutableSortedSet<String> nomsConnus() {
        return ImmutableSortedSet.copyOf(String.CASE_INSENSITIVE_ORDER,
            HColorSet.instance().names());
    }
}

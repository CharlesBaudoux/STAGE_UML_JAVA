package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.Diagram;

// Boîte à outils partagée par les tests de rendu. Ce n'est pas une classe de test : elle ne
// contient aucun @Test, seulement des aides.
//
// Elle répond à deux questions, à deux niveaux différents :
//   - le texte engendré est-il correct ? (assertCompile, lignesDeLien, aliasDeclares…)
//   - qu'est-ce que le lecteur voit vraiment ? (texteRendu, qui va jusqu'à produire le SVG et
//     en relire le texte).
//
// Le second niveau est le seul à pouvoir prouver qu'une injection ne passe pas : un texte
// dangereux peut être présent dans la source PlantUML sous forme encodée, et n'être qu'un
// texte à l'écran.
//
// Elle est dans le paquetage rendering pour atteindre PlantUmlSyntaxBuilder, qui n'est pas
// public.
public final class PlantUmlAssert {

    // Les mots par lesquels peut commencer la déclaration d'une boîte, c'est-à-dire les motCle()
    // d'UmlEntityKind. « abstract class » vient avant « class » : l'alternance d'une expression
    // régulière retient la première branche qui réussit, et l'ordre inverse ne reconnaîtrait
    // jamais la forme longue.
    private static final String MOTS_CLES_DE_BOITE =
        "abstract class|class|interface|enum|annotation";

    // « class "Nom affiché" as Alias » : le groupe 1 capture le nom, le groupe 2 l'alias. Le
    // groupe du mot-clé est non capturant (?:...) pour que la numérotation reste inchangée.
    // MULTILINE fait que ^ vaut « début de ligne » et non « début de texte ».
    private static final Pattern DECLARATION_CLASSE =
        Pattern.compile("^(?:" + MOTS_CLES_DE_BOITE + ") \"([^\"]*)\" as (\\S+)",
            Pattern.MULTILINE);

    // La même liste, pour écarter les déclarations de boîte du tri des lignes de lien.
    private static final Pattern DEBUT_DE_BOITE =
        Pattern.compile("^(?:" + MOTS_CLES_DE_BOITE + ") ");

    // Le contenu des balises <text> du SVG, c'est-à-dire ce qui est effectivement affiché.
    // DOTALL permet au point de franchir les retours à la ligne.
    private static final Pattern TEXTE_SVG =
        Pattern.compile("<text [^>]*>(.*?)</text>", Pattern.DOTALL);

    private static final Pattern ENTITE_NUMERIQUE = Pattern.compile("&#(\\d+);");

    // L'habillage que PlantUmlLayout.nomDeLien pose autour d'un label ; on le retire pour
    // comparer les lignes de lien à leur forme nue. La couleur appartient au thème de la
    // bibliothèque et disparaît sous UmlStyle.PLANTUML, d'où le groupe optionnel : l'italique,
    // lui, est commun aux deux apparences.
    private static final Pattern HABILLAGE_NOM_DE_LIEN =
        Pattern.compile("(?:<color:#[0-9A-Fa-f]{6}>)?<i>(.*)</i>(?:</color>)?");

    private PlantUmlAssert() {}

    // Le filet de sécurité de tous les tests de syntaxe : PlantUML lit-il la source sans rien
    // signaler ? PlantUML étant tolérant, il n'échoue pas toujours — d'où la vérification
    // explicite de getWarningOrError en plus de la présence d'un diagramme.
    public static void assertCompile(String source) {
        List<BlockUml> blocs = new SourceStringReader(source).getBlocks();

        assertFalse(blocs.isEmpty(),
            () -> "PlantUML n'a reconnu aucun diagramme dans la source :\n" + source);

        for (BlockUml bloc : blocs) {
            Diagram diagramme = bloc.getDiagram();
            String probleme = diagramme.getWarningOrError();
            if (probleme != null) {
                // La source complète est jointe au message : sans elle, l'échec est
                // indéchiffrable.
                fail("PlantUML a rejeté la syntaxe générée (" + diagramme.getClass().getSimpleName()
                    + ") : " + probleme.strip() + "\n--- source rejetée ---\n" + source);
            }
        }
    }

    // Sert à détecter une injection : un @enduml glissé dans un nom découperait le document en
    // plusieurs diagrammes. Le compte attendu est toujours 1.
    public static int nombreDeDiagrammes(String source) {
        return new SourceStringReader(source).getBlocks().size();
    }

    // Rejoue la séquence de PlantUmlWriter, sans écrire de fichier : les boîtes d'abord, les
    // relations ensuite, les notes en dernier.
    //
    // Toute passe ajoutée au writer doit l'être ici aussi, sans quoi les tests de rendu
    // mesureraient un document que personne ne produit jamais.
    public static String documentPour(UmlEntity... entities) {
        return documentPour(UmlStyle.PLANTUML, entities);
    }

    // La même chose sous une apparence donnée, pour les seuls tests qui parlent d'apparence.
    public static String documentPour(UmlStyle style, UmlEntity... entities) {
        return documentPour(style, UmlDiagram.defaut(), entities);
    }

    // La même chose avec des réglages d'ensemble déclarés, pour les seuls tests qui parlent du
    // titre, du sens de lecture ou des compartiments vides. Les autres passent par les surcharges
    // ci-dessus, qui décrivent un projet n'ayant rien réglé.
    public static String documentPour(UmlDiagram diagramme, UmlEntity... entities) {
        return documentPour(UmlStyle.PLANTUML, diagramme, entities);
    }

    public static String documentPour(UmlStyle style, UmlDiagram diagramme,
        UmlEntity... entities) {
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder(style, diagramme);
        builder.startDocument();
        // addClasses et non addClass en boucle : c'est ce que fait le writer, et c'est le seul
        // appel qui ouvre les rectangles de @UMLPackage.
        builder.addClasses(List.of(entities));
        for (UmlEntity entity : entities) {
            builder.addRelations(entity);
        }
        for (UmlEntity entity : entities) {
            builder.addNotes(entity);
        }
        builder.endDocument();
        return builder.build();
    }

    // Ne garde que les lignes de relation, en éliminant tout le reste par exclusion : lignes
    // vides, membres (indentés), directives @, déclarations de classe, ouvertures de groupe,
    // accolades fermantes, réglages d'ensemble, style. Ce qui subsiste est un trait.
    //
    // L'exclusion doit suivre ce que le rendu écrit : toute directive nouvelle posée en tête de
    // ligne serait sinon comptée comme un lien, et les tests de relations mesureraient autre
    // chose que ce qu'ils croient.
    public static List<String> lignesDeLien(String source) {
        return source.lines()
            .filter(l -> !l.isBlank() && !l.startsWith(" ") && !l.startsWith("@")
                && !DEBUT_DE_BOITE.matcher(l).find() && !l.startsWith("}")
                && !l.startsWith("package ") && !l.startsWith("title ")
                && !l.startsWith("left to right direction")
                && !l.startsWith("top to bottom direction")
                && !l.startsWith("skinparam") && !l.startsWith("hide ")
                && !l.startsWith("show ") && !l.startsWith("!") && !l.startsWith("note "))
            .map(String::strip)
            .map(PlantUmlAssert::sansHabillage)
            .toList();
    }

    // « $1 » réinjecte le groupe capturé : la couleur et l'italique disparaissent, le texte
    // reste. Les tests peuvent ainsi comparer au label attendu sans connaître sa mise en forme.
    private static String sansHabillage(String ligne) {
        return HABILLAGE_NOM_DE_LIEN.matcher(ligne).replaceAll("$1");
    }

    // Les alias attribués, dans l'ordre de déclaration.
    public static List<String> aliasDeclares(String source) {
        List<String> alias = new ArrayList<>();
        Matcher m = DECLARATION_CLASSE.matcher(source);
        while (m.find()) {
            alias.add(m.group(2));
        }
        return alias;
    }

    // Le niveau « ce que voit le lecteur » : on produit vraiment le SVG et on en extrait le
    // texte affiché.
    public static String texteRendu(UmlEntity... entities) throws Exception {
        String svg = svg(entities);

        // PlantUML ne lève pas d'exception en cas d'échec : il dessine une image d'erreur. Il
        // faut donc la reconnaître, sans quoi le test passerait sur un diagramme raté.
        assertFalse(svg.contains("An error has occured"),
            () -> "Le moteur PlantUML a planté sur la source générée :\n" + documentPour(entities));

        StringBuilder texte = new StringBuilder();
        Matcher m = TEXTE_SVG.matcher(svg);
        while (m.find()) {
            texte.append(decodeXml(m.group(1))).append('\n');
        }
        return texte.toString();
    }

    // Le rendu complet, en mémoire : ByteArrayOutputStream au lieu d'un fichier.
    public static String svg(UmlEntity... entities) throws Exception {
        return svg(UmlStyle.PLANTUML, entities);
    }

    public static String svg(UmlStyle style, UmlEntity... entities) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new SourceStringReader(documentPour(style, entities))
            .outputImage(os, new FileFormatOption(FileFormat.SVG));
        return os.toString(StandardCharsets.UTF_8);
    }

    // Ramène le fragment SVG au texte que le lecteur voit réellement.
    private static String decodeXml(String fragment) {
        // Les entités numériques d'abord : sinon « &amp;#60; » (un « &#60; » littéral voulu)
        // deviendrait « < » et le test validerait l'injection qu'il est censé détecter.
        Matcher m = ENTITE_NUMERIQUE.matcher(fragment);
        StringBuilder sortie = new StringBuilder();
        while (m.find()) {
            // quoteReplacement neutralise les « $ » et « \ » du remplacement, qui auraient
            // sinon un sens spécial pour appendReplacement.
            m.appendReplacement(sortie,
                Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        // Recopie ce qui suit la dernière correspondance : sans cela, la fin serait perdue.
        m.appendTail(sortie);

        return sortie.toString()
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            // PlantUML insère des espaces insécables dans le SVG ; on les ramène à l'espace
            // ordinaire pour que les comparaisons de texte soient possibles.
            .replace('\u00A0', ' ');
    }

    // Les noms affichés, dans l'ordre de déclaration.
    public static List<String> nomsDeclares(String source) {
        List<String> noms = new ArrayList<>();
        Matcher m = DECLARATION_CLASSE.matcher(source);
        while (m.find()) {
            noms.add(m.group(1));
        }
        return noms;
    }
}

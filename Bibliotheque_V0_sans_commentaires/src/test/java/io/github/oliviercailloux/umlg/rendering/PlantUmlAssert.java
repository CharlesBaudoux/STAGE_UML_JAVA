package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.oliviercailloux.umlg.UmlStyle;
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

    private static final String MOTS_CLES_DE_BOITE =
        "abstract class|class|interface|enum|annotation";

    private static final Pattern DECLARATION_CLASSE =
        Pattern.compile("^(?:" + MOTS_CLES_DE_BOITE + ") \"([^\"]*)\" as (\\S+)",
            Pattern.MULTILINE);

    private static final Pattern DEBUT_DE_BOITE =
        Pattern.compile("^(?:" + MOTS_CLES_DE_BOITE + ") ");

    private static final Pattern TEXTE_SVG =
        Pattern.compile("<text [^>]*>(.*?)</text>", Pattern.DOTALL);

    private static final Pattern ENTITE_NUMERIQUE = Pattern.compile("&#(\\d+);");

    private static final Pattern HABILLAGE_NOM_DE_LIEN =
        Pattern.compile("(?:<color:#[0-9A-Fa-f]{6}>)?<i>(.*)</i>(?:</color>)?");

    private PlantUmlAssert() {}

    public static void assertCompile(String source) {
        List<BlockUml> blocs = new SourceStringReader(source).getBlocks();

        assertFalse(blocs.isEmpty(),
            () -> "PlantUML n'a reconnu aucun diagramme dans la source :\n" + source);

        for (BlockUml bloc : blocs) {
            Diagram diagramme = bloc.getDiagram();
            String probleme = diagramme.getWarningOrError();
            if (probleme != null) {
                fail("PlantUML a rejeté la syntaxe générée (" + diagramme.getClass().getSimpleName()
                    + ") : " + probleme.strip() + "\n--- source rejetée ---\n" + source);
            }
        }
    }

    public static int nombreDeDiagrammes(String source) {
        return new SourceStringReader(source).getBlocks().size();
    }

    public static String documentPour(UmlEntity... entities) {
        return documentPour(UmlStyle.PLANTUML, entities);
    }

    public static String documentPour(UmlStyle style, UmlEntity... entities) {
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder(style);
        builder.startDocument();
        for (UmlEntity entity : entities) {
            builder.addClass(entity);
        }
        for (UmlEntity entity : entities) {
            builder.addRelations(entity);
        }
        for (UmlEntity entity : entities) {
            builder.addNotes(entity);
        }
        builder.endDocument();
        return builder.build();
    }

    public static List<String> lignesDeLien(String source) {
        return source.lines()
            .filter(l -> !l.isBlank() && !l.startsWith(" ") && !l.startsWith("@")
                && !DEBUT_DE_BOITE.matcher(l).find() && !l.startsWith("}")
                && !l.startsWith("skinparam") && !l.startsWith("hide ")
                && !l.startsWith("show ") && !l.startsWith("!") && !l.startsWith("note "))
            .map(String::strip)
            .map(PlantUmlAssert::sansHabillage)
            .toList();
    }

    private static String sansHabillage(String ligne) {
        return HABILLAGE_NOM_DE_LIEN.matcher(ligne).replaceAll("$1");
    }

    public static List<String> aliasDeclares(String source) {
        List<String> alias = new ArrayList<>();
        Matcher m = DECLARATION_CLASSE.matcher(source);
        while (m.find()) {
            alias.add(m.group(2));
        }
        return alias;
    }

    public static String texteRendu(UmlEntity... entities) throws Exception {
        String svg = svg(entities);

        assertFalse(svg.contains("An error has occured"),
            () -> "Le moteur PlantUML a planté sur la source générée :\n" + documentPour(entities));

        StringBuilder texte = new StringBuilder();
        Matcher m = TEXTE_SVG.matcher(svg);
        while (m.find()) {
            texte.append(decodeXml(m.group(1))).append('\n');
        }
        return texte.toString();
    }

    public static String svg(UmlEntity... entities) throws Exception {
        return svg(UmlStyle.PLANTUML, entities);
    }

    public static String svg(UmlStyle style, UmlEntity... entities) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new SourceStringReader(documentPour(style, entities))
            .outputImage(os, new FileFormatOption(FileFormat.SVG));
        return os.toString(StandardCharsets.UTF_8);
    }

    private static String decodeXml(String fragment) {
        Matcher m = ENTITE_NUMERIQUE.matcher(fragment);
        StringBuilder sortie = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sortie,
                Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        m.appendTail(sortie);

        return sortie.toString()
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace('\u00A0', ' ');
    }

    public static List<String> nomsDeclares(String source) {
        List<String> noms = new ArrayList<>();
        Matcher m = DECLARATION_CLASSE.matcher(source);
        while (m.find()) {
            noms.add(m.group(1));
        }
        return noms;
    }
}

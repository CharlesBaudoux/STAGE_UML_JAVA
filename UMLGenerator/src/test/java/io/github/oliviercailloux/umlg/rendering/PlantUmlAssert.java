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

public final class PlantUmlAssert {

    private static final String BOX_WORDS_KEYS =
        "abstract class|class|interface|enum|annotation";

    private static final Pattern DECLARATION_CLASS =
        Pattern.compile("^(?:" + BOX_WORDS_KEYS + ") \"([^\"]*)\" as (\\S+)",
            Pattern.MULTILINE);

    private static final Pattern BOX_START =
        Pattern.compile("^(?:" + BOX_WORDS_KEYS + ") ");

    private static final Pattern TEXT_SVG =
        Pattern.compile("<text [^>]*>(.*?)</text>", Pattern.DOTALL);

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(\\d+);");

    private static final Pattern LINK_STYLING_NAME =
        Pattern.compile("(?:<color:#[0-9A-Fa-f]{6}>)?<i>(.*)</i>(?:</color>)?");

    private PlantUmlAssert() {}

    public static void assertCompile(String source) {
        List<BlockUml> blocks = new SourceStringReader(source).getBlocks();

        assertFalse(blocks.isEmpty(),
            () -> "PlantUML recognized no diagram in the source:\n" + source);

        for (BlockUml block : blocks) {
            Diagram diagram = block.getDiagram();
            String problem = diagram.getWarningOrError();
            if (problem != null) {
                fail("PlantUML rejected the generated syntax (" + diagram.getClass().getSimpleName()
                    + "): " + problem.strip() + "\n--- rejected source ---\n" + source);
            }
        }
    }

    public static int diagramsCount(String source) {
        return new SourceStringReader(source).getBlocks().size();
    }

    public static String documentFor(UmlEntity... entities) {
        return documentFor(UmlStyle.PLANTUML, entities);
    }

    public static String documentFor(UmlStyle style, UmlEntity... entities) {
        return documentFor(style, UmlDiagram.defaults(), entities);
    }

    public static String documentFor(UmlDiagram diagram, UmlEntity... entities) {
        return documentFor(UmlStyle.PLANTUML, diagram, entities);
    }

    public static String documentFor(UmlStyle style, UmlDiagram diagram,
        UmlEntity... entities) {
        return new PlantUmlSyntaxBuilder(style, diagram).document(List.of(entities)).build();
    }

    public static List<String> linkLines(String source) {
        return source.lines()
            .filter(l -> !l.isBlank() && !l.startsWith(" ") && !l.startsWith("@")
                && !BOX_START.matcher(l).find() && !l.startsWith("}")
                && !l.startsWith("package ") && !l.startsWith("title ")
                && !l.startsWith("left to right direction")
                && !l.startsWith("top to bottom direction")
                && !l.startsWith("skinparam") && !l.startsWith("hide ")
                && !l.startsWith("show ") && !l.startsWith("!") && !l.startsWith("note "))
            .map(String::strip)
            .map(PlantUmlAssert::withoutStyling)
            .toList();
    }

    private static String withoutStyling(String line) {
        return LINK_STYLING_NAME.matcher(line).replaceAll("$1");
    }

    public static List<String> aliasDeclared(String source) {
        List<String> alias = new ArrayList<>();
        Matcher m = DECLARATION_CLASS.matcher(source);
        while (m.find()) {
            alias.add(m.group(2));
        }
        return alias;
    }

    public static String textRendered(UmlEntity... entities) throws Exception {
        String svg = svg(entities);

        assertFalse(svg.contains("An error has occured"),
            () -> "The PlantUML engine crashed on the generated source:\n" + documentFor(entities));

        StringBuilder text = new StringBuilder();
        Matcher m = TEXT_SVG.matcher(svg);
        while (m.find()) {
            text.append(decodeXml(m.group(1))).append('\n');
        }
        return text.toString();
    }

    public static String svg(UmlEntity... entities) throws Exception {
        return svg(UmlStyle.PLANTUML, entities);
    }

    public static String svg(UmlStyle style, UmlEntity... entities) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new SourceStringReader(documentFor(style, entities))
            .outputImage(os, new FileFormatOption(FileFormat.SVG));
        return os.toString(StandardCharsets.UTF_8);
    }

    private static String decodeXml(String fragment) {
        Matcher m = NUMERIC_ENTITY.matcher(fragment);
        StringBuilder output = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(output,
                Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        m.appendTail(output);

        return output.toString()
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace('\u00A0', ' ');
    }

    public static List<String> namesDeclared(String source) {
        List<String> names = new ArrayList<>();
        Matcher m = DECLARATION_CLASS.matcher(source);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}

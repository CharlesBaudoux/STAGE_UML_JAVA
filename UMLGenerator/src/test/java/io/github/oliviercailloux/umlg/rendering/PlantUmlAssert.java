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

// Toolbox shared by the rendering tests. This is not a test class: it holds no @Test, only
// helpers.
//
// It answers two questions, at two different levels:
//   - is the generated text correct? (assertCompile, linkLines, aliasDeclared…)
//   - what does the reader really see? (textRendered, which goes as far as producing the SVG and
//     reading its text back).
//
// The second level is the only one able to prove that an injection does not get through: a
// dangerous text may be present in the PlantUML source in encoded form, and be nothing but text
// on screen.
//
// It sits in the rendering package so as to reach PlantUmlSyntaxBuilder, which is not public.
public final class PlantUmlAssert {

    // The words a box declaration may start with, that is, the keyword() values of UmlEntityKind.
    // "abstract class" comes before "class": the alternation of a regular expression keeps the
    // first branch that succeeds, and the reverse order would never recognize the long form.
    private static final String BOX_WORDS_KEYS =
        "abstract class|class|interface|enum|annotation";

    // "class \"Displayed name\" as Alias": group 1 captures the name, group 2 the alias. The
    // keyword group is non-capturing (?:...) so that the numbering stays unchanged. MULTILINE makes
    // ^ mean "start of line" and not "start of text".
    private static final Pattern DECLARATION_CLASS =
        Pattern.compile("^(?:" + BOX_WORDS_KEYS + ") \"([^\"]*)\" as (\\S+)",
            Pattern.MULTILINE);

    // The same list, used to drop box declarations when sorting out link lines.
    private static final Pattern BOX_START =
        Pattern.compile("^(?:" + BOX_WORDS_KEYS + ") ");

    // The content of the SVG <text> tags, that is, what is actually displayed. DOTALL lets the dot
    // cross line breaks.
    private static final Pattern TEXT_SVG =
        Pattern.compile("<text [^>]*>(.*?)</text>", Pattern.DOTALL);

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(\\d+);");

    // The styling PlantUmlLayout.linkName sets around a label; it is removed in order to compare
    // link lines to their bare form. The colour belongs to the library theme and vanishes under
    // UmlStyle.PLANTUML, hence the optional group: the italics, for their part, are shared by both
    // appearances.
    private static final Pattern LINK_STYLING_NAME =
        Pattern.compile("(?:<color:#[0-9A-Fa-f]{6}>)?<i>(.*)</i>(?:</color>)?");

    private PlantUmlAssert() {}

    // The safety net of every syntax test: does PlantUML read the source without reporting
    // anything? PlantUML being lenient, it does not always fail — hence the explicit check of
    // getWarningOrError on top of the presence of a diagram.
    public static void assertCompile(String source) {
        List<BlockUml> blocks = new SourceStringReader(source).getBlocks();

        assertFalse(blocks.isEmpty(),
            () -> "PlantUML recognized no diagram in the source:\n" + source);

        for (BlockUml block : blocks) {
            Diagram diagram = block.getDiagram();
            String problem = diagram.getWarningOrError();
            if (problem != null) {
                // The whole source is attached to the message: without it, the failure is
                // undecipherable.
                fail("PlantUML rejected the generated syntax (" + diagram.getClass().getSimpleName()
                    + "): " + problem.strip() + "\n--- rejected source ---\n" + source);
            }
        }
    }

    // Used to detect an injection: an @enduml slipped into a name would cut the document into
    // several diagrams. The expected count is always 1.
    public static int diagramsCount(String source) {
        return new SourceStringReader(source).getBlocks().size();
    }

    // Replays the sequence of PlantUmlWriter, without writing a file: the boxes first, then the
    // relations, and the notes last.
    //
    // Any pass added to the writer must be added here too; otherwise the rendering tests would
    // measure a document nobody ever produces.
    public static String documentFor(UmlEntity... entities) {
        return documentFor(UmlStyle.PLANTUML, entities);
    }

    // The same thing under a given appearance, for the tests that speak of appearance only.
    public static String documentFor(UmlStyle style, UmlEntity... entities) {
        return documentFor(style, UmlDiagram.defaults(), entities);
    }

    // The same thing with declared overall settings, for the tests that speak of the title, the
    // reading direction or the empty compartments only. The others go through the overloads above,
    // which describe a project having set nothing.
    public static String documentFor(UmlDiagram diagram, UmlEntity... entities) {
        return documentFor(UmlStyle.PLANTUML, diagram, entities);
    }

    public static String documentFor(UmlStyle style, UmlDiagram diagram,
        UmlEntity... entities) {
        // The sequence of passes is no longer copied here: the writer's own is called.
        //
        // It used to be, with a comment warning that it would have to be kept up to date. It was
        // not: the day the merging of reciprocal associations entered the writer, this helper went
        // on writing the old document, and most of the rendering tests — every test going through
        // it, that is, the largest part of the suite — went on measuring a drawing the library no
        // longer produced, all of them green. A test helper that reproduces the code under test
        // soon tests nothing but itself.
        return new PlantUmlSyntaxBuilder(style, diagram).document(List.of(entities)).build();
    }

    // Keeps the relation lines only, ruling out everything else by exclusion: empty lines, members
    // (indented), @ directives, class declarations, group openings, closing braces, overall
    // settings, style. What is left is a stroke.
    //
    // The exclusion must follow what the rendering writes: any new directive set at the head of a
    // line would otherwise be counted as a link, and the relation tests would measure something
    // other than what they believe.
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

    // "$1" puts the captured group back: the colour and the italics vanish, the text stays. Tests
    // can thus compare against the expected label without knowing how it is laid out.
    private static String withoutStyling(String line) {
        return LINK_STYLING_NAME.matcher(line).replaceAll("$1");
    }

    // The aliases assigned, in declaration order.
    public static List<String> aliasDeclared(String source) {
        List<String> alias = new ArrayList<>();
        Matcher m = DECLARATION_CLASS.matcher(source);
        while (m.find()) {
            alias.add(m.group(2));
        }
        return alias;
    }

    // The "what the reader sees" level: the SVG is really produced, and the displayed text is
    // extracted from it.
    public static String textRendered(UmlEntity... entities) throws Exception {
        String svg = svg(entities);

        // PlantUML raises no exception on failure: it draws an error image. It must therefore be
        // recognized, otherwise the test would pass on a botched diagram.
        assertFalse(svg.contains("An error has occured"),
            () -> "The PlantUML engine crashed on the generated source:\n" + documentFor(entities));

        StringBuilder text = new StringBuilder();
        Matcher m = TEXT_SVG.matcher(svg);
        while (m.find()) {
            text.append(decodeXml(m.group(1))).append('\n');
        }
        return text.toString();
    }

    // The whole rendering, in memory: a ByteArrayOutputStream rather than a file.
    public static String svg(UmlEntity... entities) throws Exception {
        return svg(UmlStyle.PLANTUML, entities);
    }

    public static String svg(UmlStyle style, UmlEntity... entities) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new SourceStringReader(documentFor(style, entities))
            .outputImage(os, new FileFormatOption(FileFormat.SVG));
        return os.toString(StandardCharsets.UTF_8);
    }

    // Brings the SVG fragment back to the text the reader really sees.
    private static String decodeXml(String fragment) {
        // Numeric entities first: otherwise "&amp;#60;" (a deliberate literal "&#60;") would
        // become "<" and the test would bless the very injection it is meant to detect.
        Matcher m = NUMERIC_ENTITY.matcher(fragment);
        StringBuilder output = new StringBuilder();
        while (m.find()) {
            // quoteReplacement neutralizes the "$" and "\" of the replacement, which would
            // otherwise carry a special meaning for appendReplacement.
            m.appendReplacement(output,
                Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        // Copies what follows the last match: without it, the end would be lost.
        m.appendTail(output);

        return output.toString()
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            // PlantUML inserts non-breaking spaces in the SVG; they are brought back to the
            // ordinary space so that text comparisons are possible.
            .replace('\u00A0', ' ');
    }

    // The displayed names, in declaration order.
    public static List<String> namesDeclared(String source) {
        List<String> names = new ArrayList<>();
        Matcher m = DECLARATION_CLASS.matcher(source);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}

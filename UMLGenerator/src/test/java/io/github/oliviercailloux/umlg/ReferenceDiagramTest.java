package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

// The reference test: a whole diagram, compared to a file written by hand.
//
// CE QU'IL PROUVE, ET CE QU'IL NE PROUVE PAS
//
// The other tests check fragments — "the field line contains this". None
// looks at the document as a whole, so that a global regression would escape them:
// a rendering pass called in the wrong order, one empty line too many, a note written
// before its box, a style block changed by mistake.
//
// This test fixes the whole. It says "the output has not changed", never "the output is
// correct": that second question stays the business of the targeted tests, which know why each
// chunk must be what it is. The two are complementary, and only one of this
// kind — a collection of reference files becomes a tax, every legitimate change
// cassant dix d'un coup.
//
// WHEN IT FAILS
//
// Two cases, and they must be told apart before touching anything. Either the library
// regressed, and that is what gets fixed. Or the change was intended, and the reference file is
// the reference up to date — after reading the difference line by line, which is precisely the moment when
// one checks that the change really does what one believed.
//
// ON ORDER, WHICH IS THE WHOLE SUBJECT OF SUCH A TEST
//
// Comparing a whole document also freezes the order of its lines. Three orders come into
// at play, and they are not equally solid.
//
// The box order is decided here, entity by entity. The document therefore does not depend on the order
// in which the file system returns the files of a directory, which no standard
// guarantees — that is why this test enumerates rather than scans.
//
// The relation order comes from EntityExtractor, which calls its extractors in a written order:
// associations, then dependencies, then inheritances. Hence an "extends" written after the link
// association classes of the same class, which surprises on reading but is perfectly stable.
//
// The member order, lastly, is the one getDeclaredFields and getDeclaredMethods return. The
// the Java specification does not define it, and measurement confirms it: it is not the order of
// declaration of the source. It is stable for a given virtual machine, not guaranteed from one
// implementation to the next. If this test ever fails on the member order alone, that is
// therefore not a regression of the library: this is the point to deal with, by sorting
// the members at extraction time.
class ReferenceDiagramTest {

    private static final String REFERENCE_FILE = "/reference-diagram.puml";

    // The rules of the fixture project: what it holds to be noise, named by patterns.
    //
    // Nothing is hidden by default, and this test is the best place to check it: three
    // patterns are enough to drop everything that fixture counts as plumbing — the accessor
    // getName(), and the two methods javac adds to the Status enumeration. The expected document
    // did not change by one line the day the rules file stopped knowing about
    // "families" and read nothing but names.
    private static final Rules RULES = Rules.read("""
        { "hide": ["get*", "values", "valueOf"] }
        """, "the rules of the reference project");

    // The entities of the diagram, in the order they will be declared: the targets before what
    // aims at them, which makes the generated file readable from top to bottom.
    //
    // This order is not the document one: the classes @UMLPackage files in a group
    // are written together, in their rectangle, after those having none. It is the rendering
    // that decides it, and the reference file shows it.
    private static ImmutableList<UmlEntity> entities() {
        return ImmutableList.of(
            EntityExtractor.extract(ReferenceProject.Identifiable.class, RULES),
            EntityExtractor.extract(ReferenceProject.Person.class, RULES),
            EntityExtractor.extract(ReferenceProject.Status.class, RULES),
            EntityExtractor.extract(ReferenceProject.Order.class, RULES),
            EntityExtractor.extract(ReferenceProject.ServiceBank.class, RULES),
            EntityExtractor.extract(ReferenceProject.Settlement.class, RULES),
            EntityExtractor.extract(ReferenceProject.ClientInvoice.class, RULES));
    }

    private static String documentGenerated() {
        return PlantUmlAssert.documentFor(entities().toArray(new UmlEntity[0]));
    }

    private static String reference() throws Exception {
        try (InputStream stream = ReferenceDiagramTest.class
            .getResourceAsStream(REFERENCE_FILE)) {

            assertNotNull(stream, "Reference file not found: " + REFERENCE_FILE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theGeneratedDocument_isTheOneWrittenByHand() throws Exception {
        // assertEquals on the whole string: that is what gives a readable line-by-line difference
        // in the JUnit report as in the IDE.
        assertEquals(reference(), documentGenerated());
    }

    @Test
    void theGeneratedDocument_isAcceptableToPlantUml() {
        // The reference could be stable and wrong. This second assertion keeps it valid:
        // the text must be a diagram PlantUML agrees to draw.
        PlantUmlAssert.assertCompile(documentGenerated());
    }

    @Test
    void theGeneratedDocument_formsASingleDiagram() {
        // The fixture holds free texts — notes, constraints, labels. None must
        // being able to cut the document in two.
        assertEquals(1, PlantUmlAssert.diagramsCount(documentGenerated()));
    }
}

package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.model.UmlInheritanceType;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import io.github.oliviercailloux.umlg.model.UmlView;
import java.util.List;
import org.junit.jupiter.api.Test;

// Splitting the project into views: what each keeps, and what it does with the strokes
// come out.
class ViewBuilderTest {

    private static final String PAQ = "com.example";
    private static final String Other = "com.other";

    private static UmlEntity box(String pkg, String name, String... targets) {
        ImmutableList.Builder<UmlAssociation> links = ImmutableList.builder();
        for (String target : targets) {
            links.add(UmlAssociation.builder("field", target).build());
        }
        return UmlEntity.builder(pkg + "." + name, name).associations(links.build()).build();
    }

    private static ImmutableSet<UmlEntity> project() {
        // Invoice -> Client, Client -> Address, and Log alone in another package.
        return ImmutableSet.of(
            box(PAQ, "Invoice", PAQ + ".Client"),
            box(PAQ, "Client", PAQ + ".Address"),
            box(PAQ, "Address"),
            box(Other, "Log"));
    }

    private static ImmutableList<UmlView> views(String json) {
        return ViewBuilder.build(project(), UmlDiagram.defaults(),
            Rules.read(json, "the rules of the test"));
    }

    // The rules file declaring a single view.
    //
    // These tests exercise the building of views, not the reading of the file (see
    // RulesTest): writing the top-level object on each call would drown what changes from one test to
    // the other, which is the content of the view.
    private static ImmutableList<UmlView> oneView(String view) {
        return views("{ \"views\": [" + view + "] }");
    }

    private static List<String> names(UmlView view) {
        return view.entities().stream().map(UmlEntity::name).toList();
    }

    private static UmlView view(ImmutableList<UmlView> all, String name) {
        return all.stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }

    // --- The full view ---

    @Test
    void withoutAnyRule_onlyTheFullViewIsProduced() {
        ImmutableList<UmlView> all = views("{}");

        assertEquals(1, all.size(), all.toString());
        assertEquals(ViewBuilder.FULL_VIEW, all.get(0).name());
        assertEquals(4, all.get(0).entities().size());
    }

    @Test
    void theFullView_alwaysComesFirst() {
        // PlantUmlWriter counts on this order: the first view goes to the root of the directory, the
        // others in the subdirectory.
        assertEquals(ViewBuilder.FULL_VIEW,
            oneView("""
                { "name": "Billing", "retain": ["Invoice"] }
                """)
                    .get(0).name());
    }

    @Test
    void theFullView_takesTheUmlDiagramTitle() {
        ImmutableList<UmlView> all = ViewBuilder.build(project(),
            new UmlDiagram("Mon architecture", UMLFlow.LEFT_TO_RIGHT, true), Rules.no());

        assertEquals("Mon architecture", all.get(0).name());
        assertEquals(UMLFlow.LEFT_TO_RIGHT, all.get(0).direction());
    }

    // --- The declared views ---

    @Test
    void declaredView_keepsOnlyWhatItNames() {
        assertEquals(List.of("Invoice", "Client"),
            names(view(oneView("""
                { "name": "Billing", "retain": ["Invoice", "Client"] }
                """), "Billing")));
    }

    @Test
    void viewByPackage_keepsTheWholePackage() {
        assertEquals(List.of("Invoice", "Client", "Address"),
            names(view(oneView("""
                { "name": "Domain", "retain": [{ "package": "%s" }] }
                """.formatted(PAQ)), "Domain")));
    }

    @Test
    void view_canRemoveWhatItKept() {
        assertEquals(List.of("Invoice", "Address"),
            names(view(oneView("""
                {
                  "name": "Domain",
                  "retain": [{ "package": "%s" }],
                  "remove": ["Client"]
                }
                """.formatted(PAQ)), "Domain")));
    }

    @Test
    void view_keepsTheWritingOrder() {
        // The written order is that of the exposition accompanying the drawing: it must be kept.
        assertEquals(List.of("Address", "Invoice", "Client"),
            names(view(oneView("""
                { "name": "À l'towards", "retain": ["Address", "Invoice", "Client"] }
                """), "À l'towards")));
    }

    @Test
    void view_acceptsTheQualifiedName() {
        assertEquals(List.of("Log"),
            names(view(oneView("""
                { "name": "Elsewhere", "retain": ["%s.Log"] }
                """.formatted(Other)), "Elsewhere")));
    }

    @Test
    void view_carriesItsOwnReadingDirection() {
        // The content and the reading direction are two fields of the same object: the view is not
        // no longer declares in two steps.
        ImmutableList<UmlView> all = oneView("""
            {
              "name": "Billing",
              "retain": ["Invoice", "Client"],
              "direction": "left-to-right"
            }
            """);

        assertEquals(UMLFlow.LEFT_TO_RIGHT, view(all, "Billing").direction());
        assertEquals(UMLFlow.TOP_TO_BOTTOM, all.get(0).direction(),
            "The full view keeps its own.");
    }

    @Test
    void unknownName_isRefused() {
        // The file has no compiler: a renamed class would leave a dead name in it, and the
        // the diagram would be drawn truncated with nobody knowing.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> oneView("""
                { "name": "Fault", "retain": ["Biller"] }
                """));

        assertTrue(e.getMessage().contains("matches no class"), e.getMessage());
    }

    @Test
    void entirelyRemovedView_isRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> oneView("""
                { "name": "Empty", "retain": ["Invoice"], "remove": ["Invoice"] }
                """));
    }

    // --- Le neighborhood ---

    @Test
    void neighbourhood_takesTheClassAndWhatTouchesIt() {
        // Client aims at Address, and Invoice aims at Client: the three of them, and not Log.
        assertEquals(List.of("Invoice", "Client", "Address"),
            names(view(oneView("""
                { "name": "Around of Client", "retain": [{ "around": "Client" }] }
                """), "Around of Client")));
    }

    @Test
    void neighbourhood_doesNotGoBeyond() {
        // Around of Address: Client aims at it, but Invoice is two steps away and stays out.
        assertEquals(List.of("Client", "Address"),
            names(view(oneView("""
                { "name": "Around d'Address", "retain": [{ "around": "Address" }] }
                """), "Around d'Address")));
    }

    // --- The views computed per package ---

    @Test
    void viewsByPackage_givesOneViewPerPackage() {
        ImmutableList<UmlView> all = views("""
            { "viewsByPackage": true }
            """);

        assertEquals(3, all.size(), all.toString());
        assertEquals(List.of("Log"), names(view(all, Other)));
        assertEquals(3, view(all, PAQ).entities().size());
    }

    // --- Pruning the outgoing strokes ---

    @Test
    void aLinkTowardsAMissingBox_isRemoved() {
        // Invoice aims at Client, which is not in the view: the stroke cannot be drawn, and
        // leaving it would fail the rendering on an unknown alias.
        UmlView only = view(oneView("""
            { "name": "Only", "retain": ["Invoice"] }
            """), "Only");

        assertTrue(only.entities().get(0).associations().isEmpty(),
            only.entities().get(0).relations().toString());
    }

    @Test
    void aLinkInternalToTheView_isKept() {
        UmlView two = view(oneView("""
            { "name": "Two", "retain": ["Invoice", "Client"] }
            """), "Two");

        assertEquals(1, two.entities().get(0).associations().size());
    }

    @Test
    void aBoxNoLinkLeaves_isNotCopied() {
        // Rebuilding the fourteen components was written twice, by hand, and nothing
        // would have said which one had been forgotten: it now lives in
        // UmlEntity.withRelations, which returns the entity even when there is nothing to change.
        ImmutableSet<UmlEntity> project = project();
        UmlEntity address = project.stream().filter(e -> e.name().equals("Address"))
            .findFirst().orElseThrow();

        UmlView all = ViewBuilder.build(project, UmlDiagram.defaults(),
            Rules.read("""
                { "views": [ { "name": "All", "retain": [{ "package": "%s" }] } ] }
                """.formatted(PAQ), "test")).get(1);

        assertSame(address, all.entities().get(2));
    }

    @Test
    void theFullView_removesNoLink() {
        assertEquals(1, views("{}").get(0).entities().iterator().next().associations().size());
    }

    // --- The order imposed by invisible links ---

    @Test
    void twoBoxesNothingLinks_areChained() {
        // The engine would place them side by side, at random. The written order is that of the exposition.
        UmlView ordered = view(oneView("""
            { "name": "Order", "retain": ["Address", "Log"] }
            """), "Order");

        ImmutableList<UmlLayout> links = ordered.entities().get(0).layouts();
        assertEquals(1, links.size(), ordered.entities().get(0).relations().toString());
        assertEquals(Other + ".Log", links.get(0).targetQualifiedName());
    }

    @Test
    void twoAlreadyLinkedBoxes_areNotChained() {
        // A stroke already exists between them: adding an invisible one would constrain twice the
        // the same pair, and would freeze a drawing the engine composes better than we do.
        UmlView two = view(oneView("""
            { "name": "Two", "retain": ["Invoice", "Client"] }
            """), "Two");

        assertTrue(two.entities().get(0).layouts().isEmpty(),
            two.entities().get(0).relations().toString());
    }

    // A family — one parent, three children — then two lone boxes: the exact shape of the
    // the "Mapping" diagram written by hand by the teacher.
    private static ImmutableSet<UmlEntity> family() {
        return ImmutableSet.of(
            UmlEntity.builder(PAQ + ".KeysymEntry", "KeysymEntry").build(),
            child("Mnemonic"), child("Ucp"), child("Code"),
            box(PAQ, "KeyboardMap"),
            box(PAQ, "XkbSymbolsReader"));
    }

    private static UmlEntity child(String name) {
        return UmlEntity.builder(PAQ + "." + name, name)
            .relations(List.of(new UmlInheritance(PAQ + ".KeysymEntry",
                UmlInheritanceType.GENERALIZATION)))
            .build();
    }

    // The invisible links of the view, in the form "source -> target", in simple names.
    private static List<String> chains(UmlView view) {
        return view.entities().stream()
            .flatMap(e -> e.layouts().stream()
                .map(l -> e.name() + " -> "
                    + l.targetQualifiedName().substring(l.targetQualifiedName().
                        lastIndexOf('.') + 1)))
            .toList();
    }

    @Test
    void siblings_areNotStackedUnderEachOther() {
        // The trap this test guards: Mnemonic, Ucp and Code are not linked to each other,
        // but they belong to the same block — the engine fans them out under their
        // parent, and chaining them would break the very shape of the inheritance.
        UmlView view = ViewBuilder.build(family(), UmlDiagram.defaults(),
            Rules.read("""
                { "views": [ { "name": "Mapping", "retain": ["KeysymEntry", "Mnemonic",
                    "Ucp", "Code", "KeyboardMap", "XkbSymbolsReader"] } ] }
                """, "test")).get(1);

        assertEquals(List.of("KeysymEntry -> KeyboardMap", "KeyboardMap -> XkbSymbolsReader"),
            chains(view),
            "One link per block jump, and nothing inside a block.");
    }

    @Test
    void aSelfContainedView_isNotConstrained() {
        UmlView view = ViewBuilder.build(family(), UmlDiagram.defaults(),
            Rules.read("""
                { "views": [ { "name": "Family",
                    "retain": ["KeysymEntry", "Mnemonic", "Ucp", "Code"] } ] }
                """, "test")).get(1);

        assertTrue(chains(view).isEmpty(),
            "The engine has everything it needs: " + chains(view));
    }

    @Test
    void theRepresentativeOfABlock_isItsFirstWrittenBox() {
        // The writing order also decides which box carries the link: it is the one that
        // the user named first in their block.
        UmlView view = ViewBuilder.build(family(), UmlDiagram.defaults(),
            Rules.read("""
                { "views": [ { "name": "Mapping", "retain": ["Mnemonic", "KeysymEntry",
                    "Ucp", "Code", "KeyboardMap"] } ] }
                """, "test")).get(1);

        assertEquals(List.of("Mnemonic -> KeyboardMap"), chains(view));
    }

    @Test
    void theLastBox_pullsTowardsNothing() {
        UmlView ordered = view(oneView("""
            { "name": "Order", "retain": ["Address", "Log"] }
            """), "Order");

        assertTrue(ordered.entities().get(1).layouts().isEmpty());
    }

    // --- The file name ---

    @Test
    void twoViewsComingDownToTheSameFile_areRefused() {
        // The file reader refuses two views with identical names, and "Model" is not
        // "Model!". But the identity that counts downstream is the file name, where the
        // punctuation is removed: both views were written to "views/model.svg", the second
        // erased the first without a word, the report announced two files one of which did not
        // exist, and the clickable links of the lost view led to the diagram of the other.
        IllegalArgumentException refusal =
            assertThrows(IllegalArgumentException.class, () -> views("""
                {
                  "views": [
                    { "name": "Model", "retain": ["Invoice"] },
                    { "name": "Model!", "retain": ["Client"] }
                  ]
                }
                """));

        assertTrue(refusal.getMessage().contains("\"Model\""), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("\"Model!\""), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("\"model\""), refusal.getMessage());
    }

    @Test
    void twoViewsDifferingOnlyByPunctuation_areRefused() {
        // The reduction keeps neither case, nor punctuation, nor spaces: "The mapping",
        // "The Mapping" and "The-mapping" both give "the-mapping".
        assertThrows(IllegalArgumentException.class, () -> views("""
            {
              "views": [
                { "name": "Le mapping", "retain": ["Invoice"] },
                { "name": "Le-Mapping", "retain": ["Client"] }
              ]
            }
            """));
    }

    @Test
    void aDeclaredViewAndAPackageView_doNotSilentlyOverlap() {
        // "viewsByPackage" adds names the author never wrote, hence never proofread:
        // this is the case where the collision is hardest to see from the file.
        assertThrows(IllegalArgumentException.class, () -> views("""
            {
              "viewsByPackage": true,
              "views": [ { "name": "com, other", "retain": ["Log"] } ]
            }
            """));
    }

    @Test
    void viewsWithClearlyDistinctNames_areAllProduced() {
        assertEquals(3, views("""
            {
              "views": [
                { "name": "Model", "retain": ["Invoice"] },
                { "name": "Log", "retain": ["Log"] }
              ]
            }
            """).size());
    }

    @Test
    void aViewNamedAfterTheFullView_staysAccepted() {
        // The full view alone does not draw its file name from its name: it is always written under
        // the same, and at the root rather than in the views subdirectory. It can therefore neither
        // overwrite nor be overwritten, and accusing it of a collision would be a groundless refusal.
        assertEquals(2, views("""
            { "views": [ { "name": "Project", "retain": ["Invoice"] } ] }
            """).size());
    }

    // --- The default package ---

    @Test
    void theDefaultPackageName_isInEnglish() {
        // This label is not a message: it becomes a view name, hence a diagram title,
        // hence a file name. It was the only French string the library showed.
        ImmutableList<UmlView> all = ViewBuilder.build(
            ImmutableSet.of(UmlEntity.builder("SansPaquetage", "SansPaquetage").build()),
            UmlDiagram.defaults(), Rules.read("{ \"viewsByPackage\": true }", "test"));

        assertEquals(ViewBuilder.PACKAGE_BY_DEFAULT, all.get(1).name());
        assertEquals("default", all.get(1).fileName());
    }

    @Test
    void theFileName_isCleaned() {
        assertEquals("around-address",
            new UmlView("Around Address", ImmutableList.copyOf(project()),
                UMLFlow.TOP_TO_BOTTOM).fileName());
        assertEquals("billing-model",
            new UmlView("Billing model!", ImmutableList.copyOf(project()),
                UMLFlow.TOP_TO_BOTTOM).fileName());
    }

    @Test
    void emptyProject_givesNoView() {
        // It is UmlProject that refuses a project with no class, with a useful message; failing
        // before it would put the user on a false trail.
        assertTrue(ViewBuilder.build(ImmutableSet.of(), UmlDiagram.defaults(),
            Rules.no()).isEmpty());
    }

    @Test
    void aViewChangesNeitherTheBoxesNorTheirMembers() {
        UmlEntity inFullView = views("{}").get(0).entities().iterator().next();
        UmlEntity inView =
            view(oneView("""
                { "name": "Two", "retain": ["Invoice", "Client"] }
                """), "Two").entities().get(0);

        assertEquals(inFullView.name(), inView.name());
        assertEquals(inFullView.fields(), inView.fields());
        assertFalse(inView.relations().isEmpty());
    }

}

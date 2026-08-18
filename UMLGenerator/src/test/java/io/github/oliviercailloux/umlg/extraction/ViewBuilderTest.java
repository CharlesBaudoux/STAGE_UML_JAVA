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

    private static ImmutableList<UmlView> oneView(String view) {
        return views("{ \"views\": [" + view + "] }");
    }

    private static List<String> names(UmlView view) {
        return view.entities().stream().map(UmlEntity::name).toList();
    }

    private static UmlView view(ImmutableList<UmlView> all, String name) {
        return all.stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void withoutAnyRule_onlyTheFullViewIsProduced() {
        ImmutableList<UmlView> all = views("{}");

        assertEquals(1, all.size(), all.toString());
        assertEquals(ViewBuilder.FULL_VIEW, all.get(0).name());
        assertEquals(4, all.get(0).entities().size());
    }

    @Test
    void theFullView_alwaysComesFirst() {
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

    @Test
    void neighbourhood_takesTheClassAndWhatTouchesIt() {
        assertEquals(List.of("Invoice", "Client", "Address"),
            names(view(oneView("""
                { "name": "Around of Client", "retain": [{ "around": "Client" }] }
                """), "Around of Client")));
    }

    @Test
    void neighbourhood_doesNotGoBeyond() {
        assertEquals(List.of("Client", "Address"),
            names(view(oneView("""
                { "name": "Around d'Address", "retain": [{ "around": "Address" }] }
                """), "Around d'Address")));
    }

    @Test
    void viewsByPackage_givesOneViewPerPackage() {
        ImmutableList<UmlView> all = views("""
            { "viewsByPackage": true }
            """);

        assertEquals(3, all.size(), all.toString());
        assertEquals(List.of("Log"), names(view(all, Other)));
        assertEquals(3, view(all, PAQ).entities().size());
    }

    @Test
    void aLinkTowardsAMissingBox_isRemoved() {
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

    @Test
    void twoBoxesNothingLinks_areChained() {
        UmlView ordered = view(oneView("""
            { "name": "Order", "retain": ["Address", "Log"] }
            """), "Order");

        ImmutableList<UmlLayout> links = ordered.entities().get(0).layouts();
        assertEquals(1, links.size(), ordered.entities().get(0).relations().toString());
        assertEquals(Other + ".Log", links.get(0).targetQualifiedName());
    }

    @Test
    void twoAlreadyLinkedBoxes_areNotChained() {
        UmlView two = view(oneView("""
            { "name": "Two", "retain": ["Invoice", "Client"] }
            """), "Two");

        assertTrue(two.entities().get(0).layouts().isEmpty(),
            two.entities().get(0).relations().toString());
    }

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

    @Test
    void twoViewsComingDownToTheSameFile_areRefused() {
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
        assertEquals(2, views("""
            { "views": [ { "name": "Project", "retain": ["Invoice"] } ] }
            """).size());
    }

    @Test
    void theDefaultPackageName_isInEnglish() {
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

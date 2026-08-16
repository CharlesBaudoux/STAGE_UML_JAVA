package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.model.UmlView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// What the views leave on disk: the tree, and the fate of the previous
// previous.
class PlantUmlWrittenViewsTest {

    private static UmlEntity box(String name) {
        return UmlEntity.builder("com.example." + name, name).build();
    }

    // A project with three views: the full one, then two declared views.
    private static UmlProject project() {
        ImmutableList<UmlEntity> all = ImmutableList.of(box("Invoice"), box("Client"));

        return UmlProject.given(Set.copyOf(all), UmlDiagram.defaults(), List.of(
            new UmlView("Project", all, UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Billing", ImmutableList.of(box("Invoice")), UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Clients", ImmutableList.of(box("Client")), UMLFlow.LEFT_TO_RIGHT)));
    }

    private static List<String> files(Path folder) throws IOException {
        try (Stream<Path> stream = Files.list(folder)) {
            return stream.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    // --- The call, written once ---
    //
    // Almost every test of this file writes the same views in the same place; a handful
    // merely vary the project, the format or the fate of the previous generation. Each
    // the overload below therefore says exactly what its caller varies, and the day
    // writeViews takes one more argument, there will be a single place to change.

    // The output directory, always the same: "diagrams" under the test directory.
    private static Path folder(Path tmp) {
        return tmp.resolve("diagrams");
    }

    private static ImmutableList<Path> write(Path tmp) throws IOException {
        return write(project(), tmp, ".svg", false);
    }

    private static ImmutableList<Path> write(UmlProject project, Path tmp) throws IOException {
        return write(project, tmp, ".svg", false);
    }

    private static ImmutableList<Path> write(UmlProject project, Path tmp, String extension,
        boolean keepPrevious) throws IOException {
        return PlantUmlWriter.writeViews(project, folder(tmp), extension, UmlStyle.PLANTUML,
            keepPrevious);
    }

    @Test
    void theFullViewIsAtTheRoot_theOthersInASubdirectory(@TempDir Path tmp) throws Exception {
        Path folder = folder(tmp);

        ImmutableList<Path> written = write(tmp);

        assertEquals(3, written.size(), written.toString());
        // The .puml source goes with each image: it is what is touched up and compared.
        assertEquals(List.of("project.puml", "project.svg", "views"), files(folder));
        assertEquals(List.of("billing.puml", "billing.svg", "clients.puml",
            "clients.svg"), files(folder.resolve("views")));
    }

    @Test
    void eachFile_containsItsOwnViewOnly(@TempDir Path tmp) throws Exception {
        Path folder = folder(tmp);
        write(tmp);

        String billing =
            Files.readString(folder.resolve("views/billing.svg"), StandardCharsets.UTF_8);

        assertTrue(billing.contains("Invoice"), "The kept class must be drawn.");
        assertFalse(billing.contains("Client"), "The one the view drops must not be drawn.");
    }

    @Test
    void eachView_carriesItsTitle(@TempDir Path tmp) throws Exception {
        Path folder = folder(tmp);
        write(tmp);

        assertTrue(Files.readString(folder.resolve("views/clients.svg"), StandardCharsets.UTF_8)
            .contains("Clients"), "The name of the view serves as its title.");
    }

    @Test
    void noErrorImage(@TempDir Path tmp) throws Exception {
        Path folder = folder(tmp);
        write(tmp);

        for (Path file : List.of(folder.resolve("project.svg"),
            folder.resolve("views/billing.svg"))) {
            assertFalse(
                Files.readString(file, StandardCharsets.UTF_8).toLowerCase()
                    .contains("syntax error"),
                "PlantUML a rendered one image d'error : " + file);
        }
    }

    @Test
    void theFullViewKeepsItsFileName_evenWithATitle(@TempDir Path tmp) throws Exception {
        // Its title is declared and can change; its path must not — it is the
        // the diagram one opens first, and the one a documentation points to.
        ImmutableList<UmlEntity> all = ImmutableList.of(box("Invoice"));
        UmlProject project = UmlProject.given(Set.copyOf(all), UmlDiagram.defaults(),
            List.of(new UmlView("Architecture of the Billing module", all,
                UMLFlow.TOP_TO_BOTTOM)));
        Path folder = folder(tmp);

        write(project, tmp);

        assertTrue(Files.exists(folder.resolve("project.svg")), files(folder).toString());
        assertTrue(Files.readString(folder.resolve("project.svg"), StandardCharsets.UTF_8)
            .contains("Architecture of the Billing module"), "The title itself is indeed written.");
    }

    @Test
    void pngFormat_isAccepted(@TempDir Path tmp) throws Exception {
        write(project(), tmp, ".png", false);

        assertEquals(List.of("project.png", "project.puml", "views"), files(folder(tmp)));
    }

    // --- Navigating from one view to another ---

    @Test
    void fromTheFullView_eachBoxLeadsToItsOwnView(@TempDir Path tmp) throws Exception {
        // A diagram directory is but a collection of images as long as one cannot
        // go from one to the other. From the overview view, clicking a class must
        // open the one detailing it.
        Path folder = folder(tmp);
        write(tmp);

        String fullView =
            Files.readString(folder.resolve("project.svg"), StandardCharsets.UTF_8);

        assertTrue(fullView.contains("views/billing.svg"), "The link goes down to the subdirectory.");
        assertTrue(fullView.contains("views/clients.svg"), fullView.substring(0, 400));
    }

    @Test
    void betweenTwoViews_thePathIsTheNeighbourOne(@TempDir Path tmp) throws Exception {
        // From "views/billing.svg", the other views are neighbours: the path does not
        // does not go back through the root.
        Path folder = folder(tmp);
        write(tmp);

        String clients =
            Files.readString(folder.resolve("views/clients.svg"), StandardCharsets.UTF_8);

        assertFalse(clients.contains("views/views/"), "No path is doubled.");
    }

    @Test
    void aView_doesNotPointToItself(@TempDir Path tmp) throws Exception {
        // The link would lead nowhere, and would suggest there is something else to see.
        Path folder = folder(tmp);
        write(tmp);

        String billing =
            Files.readString(folder.resolve("views/billing.svg"), StandardCharsets.UTF_8);

        assertFalse(billing.contains("billing.svg"), billing.substring(0, 400));
    }

    @Test
    void anAdornedAndClickableBox_isAcceptedByTheEngine(@TempDir Path tmp) throws Exception {
        // The trap this test guards: the order of the chunks of a box header is not
        // free, and the engine does not forgive. Nothing documents it, and only the case where a box
        // carries at once a stereotype, a colour AND a navigation link reveals it — the
        // tests exercising one without the others all passed.
        UmlEntity adorned = UmlEntity.builder("com.example.Animal", "Animal")
            .stereotype("entity").color("#EEEEEE").build();
        ImmutableList<UmlEntity> all = ImmutableList.of(box("Invoice"), adorned);

        UmlProject project = UmlProject.given(Set.copyOf(all), UmlDiagram.defaults(), List.of(
            new UmlView("Project", all, UMLFlow.TOP_TO_BOTTOM),
            new UmlView("The living", ImmutableList.of(adorned), UMLFlow.TOP_TO_BOTTOM)));

        // writeViews fails if the engine rejects the source: the check is in the writer,
        // and it is what caught the defect rather than delivering an error image.
        write(project, tmp);

        String fullView =
            Files.readString(folder(tmp).resolve("project.svg"), StandardCharsets.UTF_8);

        assertTrue(fullView.contains("views/the-living.svg"), "The link is indeed set.");
        assertTrue(fullView.contains("entity"), "And the stereotype stays written.");
        assertFalse(fullView.toLowerCase().contains("syntax error"), "No image d'error.");
    }

    // --- Two views, a single file ---

    @Test
    void twoViewsWithTheSameFileName_areRefused(@TempDir Path tmp) {
        // UmlView.fileName reduces "Model!" and "Model" to the same "model": the second
        // view overwrote the first without a word, the report announced two files one of which
        // did not exist, and the navigation links led to the wrong diagram.
        //
        // The main check belongs to ViewBuilder, the only place where all the views
        // are met; this one is the belt, and it holds for every caller building a
        // UmlProject without going through it.
        ImmutableList<UmlEntity> all = ImmutableList.of(box("Invoice"), box("Client"));
        UmlProject project = UmlProject.given(Set.copyOf(all), UmlDiagram.defaults(), List.of(
            new UmlView("Project", all, UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Model", ImmutableList.of(box("Invoice")), UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Model!", ImmutableList.of(box("Client")), UMLFlow.TOP_TO_BOTTOM)));

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
            () -> write(project, tmp));

        assertTrue(refusal.getMessage().contains("Model"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("Model"),
            "The message must name both views: " + refusal.getMessage());
        assertTrue(refusal.getMessage().contains("model.svg"),
            "And the file they fight over: " + refusal.getMessage());
    }

    @Test
    void twoViewsWithDistinctNames_areBothWritten(@TempDir Path tmp) throws Exception {
        // The counterpart of the previous one: the guard must refuse the real collision only.
        write(tmp);

        assertEquals(List.of("billing.puml", "billing.svg", "clients.puml",
            "clients.svg"), files(folder(tmp).resolve("views")));
    }

    // --- What becomes of the previous generation ---

    @Test
    void replace_erasesDiagramsThatBecameUseless(@TempDir Path tmp) throws Exception {
        Path folder = folder(tmp);
        Files.createDirectories(folder.resolve("views"));
        // A view from a past generation, which the rules file no longer declares.
        Path ghost = folder.resolve("views/ancienne-view.svg");
        Files.writeString(ghost, "<svg/>", StandardCharsets.UTF_8);

        write(tmp);

        assertFalse(Files.exists(ghost),
            "An image nothing updates any more lies all the better for being in its place.");
    }

    @Test
    void replace_doesNotTouchFilesThatAreNotOurs(@TempDir Path tmp) throws Exception {
        Path folder = folder(tmp);
        Files.createDirectories(folder);
        Path readMe = folder.resolve("README.md");
        Files.writeString(readMe, "these diagrams are generated", StandardCharsets.UTF_8);

        write(tmp);

        assertTrue(Files.exists(readMe),
            "The output directory is not emptied of what is not ours.");
    }

    @Test
    void keep_keepsThePreviousGeneration(@TempDir Path tmp) throws Exception {
        write(project(), tmp, ".svg", true);
        write(project(), tmp, ".svg", true);

        assertTrue(Files.exists(folder(tmp).resolve("project.svg")), "The first one stays.");
        assertTrue(Files.exists(tmp.resolve("diagrams-2/project.svg")),
            "The second is written alongside, so that they can be compared.");
    }

    @Test
    void keep_numbersAsManyTimesAsNeeded(@TempDir Path tmp) throws Exception {
        for (int i = 0; i < 3; i++) {
            write(project(), tmp, ".svg", true);
        }
        assertTrue(Files.exists(tmp.resolve("diagrams-3/project.svg")), files(tmp).toString());
    }
}

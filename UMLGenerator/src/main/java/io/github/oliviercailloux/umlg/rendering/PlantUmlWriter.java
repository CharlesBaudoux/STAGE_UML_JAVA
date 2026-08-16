package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.model.UmlView;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Stream;
import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.Diagram;
import net.sourceforge.plantuml.dot.GraphvizUtils;

public class PlantUmlWriter {
    private static final String EXTENSION_SVG = ".svg";
    private static final String EXTENSION_PNG = ".png";

    private static final String EXTENSION_PUML = ".puml";

    private static final int SIDE_MAXIMUM_PNG = 16384;

    private static final String VIEWS_FOLDER = "views";

    private static final String FULL_VIEW_NAME = "project";

    private PlantUmlWriter() {}

    public static ImmutableList<Path> writeViews(UmlProject project, Path folder, String extension,
        UmlStyle style, boolean keepPrevious) throws IOException {
        checkNotNull(project);
        checkNotNull(folder);
        checkNotNull(style);
        checkArgument(supports("x" + extension),
            "The output format must be .png or .svg (got: %s)", extension);
        checkArgument(!project.views().isEmpty(),
            "This project describes no view; nothing would be written.");

        Path root = destination(folder, keepPrevious);
        Path views = root.resolve(VIEWS_FOLDER);
        Files.createDirectories(views);

        ImmutableList.Builder<Path> written = ImmutableList.builder();

        boolean fullView = true;

        Map<Path, String> writtenBy = new LinkedHashMap<>();

        Map<String, String> detailedBy = detailedBy(project, extension);

        for (UmlView view : project.views()) {
            Path file = fullView
                ? root.resolve(FULL_VIEW_NAME + extension)
                : views.resolve(view.fileName() + extension);

            String occupying = writtenBy.putIfAbsent(file, view.name());
            checkArgument(occupying == null,
                "Views \"%s\" and \"%s\" both come down to the file %s: the second one would "
                    + "overwrite the first, and nothing on the drawing would say so. Rename one "
                    + "of them.", occupying, view.name(), file);

            write(view, project.diagram(), file.toString(), style,
                navigation(view, detailedBy, fullView, extension));

            Proportions.check(file, view.name());
            written.add(file);
            fullView = false;
        }
        return written.build();
    }

    private static Path destination(Path folder, boolean keepPrevious) throws IOException {
        if (!keepPrevious) {
            clearViews(folder);
            return folder;
        }
        if (!Files.exists(folder)) {
            return folder;
        }

        for (int number = 2; number < 1000; number++) {
            Path candidate = folder.resolveSibling(folder.getFileName() + "-" + number);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Too many kept generations next to " + folder
            + ". Remove the old ones, or drop keepPrevious.");
    }

    private static void clearViews(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        for (Path root : new Path[] {folder, folder.resolve(VIEWS_FOLDER)}) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.list(root)) {
                for (Path file : files.sorted(Comparator.naturalOrder()).toList()) {
                    String name = file.getFileName().toString();

                    if (Files.isRegularFile(file)
                        && (supports(name) || name.endsWith(EXTENSION_PUML))) {
                        Files.delete(file);
                    }
                }
            }
        }
    }

    private static void write(UmlView view, UmlDiagram settings, String file, UmlStyle style,
        Map<String, String> navigation) throws IOException {
        UmlDiagram own =
            new UmlDiagram(view.name(), view.direction(), settings.hideEmptyMembers());

        write(new PlantUmlSyntaxBuilder(style, own).navigation(navigation),
            view.entities(), file);
    }

    private static Map<String, String> detailedBy(UmlProject project, String extension) {
        Map<String, String> to = new LinkedHashMap<>();
        boolean fullView = true;

        for (UmlView view : project.views()) {
            if (fullView) {
                fullView = false;
                continue;
            }
            for (UmlEntity entity : view.entities()) {
                to.putIfAbsent(entity.qualifiedName(), view.fileName() + extension);
            }
        }
        return to;
    }

    private static Map<String, String> navigation(UmlView view, Map<String, String> detailedBy,
        boolean fullView, String extension) {
        String own = view.fileName() + extension;
        Map<String, String> forThisView = new LinkedHashMap<>();

        for (UmlEntity entity : view.entities()) {
            String target = detailedBy.get(entity.qualifiedName());
            if (target == null || target.equals(own)) {
                continue;
            }

            forThisView.put(entity.qualifiedName(),
                fullView ? VIEWS_FOLDER + "/" + target : target);
        }
        return forThisView;
    }

    public static boolean supports(String outputFileName) {
        checkNotNull(outputFileName);

        String normalized = outputFileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(EXTENSION_SVG) || normalized.endsWith(EXTENSION_PNG);
    }

    private static FileFormat formatFor(String outputFileName) {
        return outputFileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION_SVG)
            ? FileFormat.SVG
            : FileFormat.PNG;
    }

    public static void write(UmlProject project, String outputFileName) throws IOException {
        write(project, outputFileName, UmlStyle.PLANTUML);
    }

    public static void write(UmlProject project, String outputFileName, UmlStyle style)
        throws IOException {
        checkNotNull(project);
        checkNotNull(outputFileName);
        checkNotNull(style);

        checkArgument(supports(outputFileName),
            "The output file name must end in .png or .svg (got: %s)", outputFileName);

        write(new PlantUmlSyntaxBuilder(style, project.diagram()), project.entities(),
            outputFileName);

        Proportions.check(Path.of(outputFileName),
            project.diagram().hasTitle() ? project.diagram().title() : outputFileName);
    }

    private static void write(PlantUmlSyntaxBuilder syntax, Iterable<UmlEntity> entities,
        String outputFileName) throws IOException {
        String codePlantUml = syntax.document(entities).build();

        FileFormat format = formatFor(outputFileName);

        SourceStringReader reader = new SourceStringReader(codePlantUml);

        checkAcceptance(reader, codePlantUml);
        drawingCheckEngine();

        int point = outputFileName.lastIndexOf('.');
        Files.writeString(Path.of(outputFileName.substring(0, point) + EXTENSION_PUML),
            codePlantUml);

        try (OutputStream os = new FileOutputStream(outputFileName)) {
            GraphvizUtils.setLocalImageLimit(SIDE_MAXIMUM_PNG);
            try {
                reader.outputImage(os, new FileFormatOption(format));
            } finally {
                GraphvizUtils.removeLocalLimitSize();
            }
        }
    }

    private static void checkAcceptance(SourceStringReader reader, String codePlantUml) {
        for (BlockUml bloc : reader.getBlocks()) {
            Diagram diagram = bloc.getDiagram();
            String problem = diagram.getWarningOrError();

            if (problem != null) {
                throw new IllegalStateException(
                    "The generated PlantUML source was rejected by the rendering engine ("
                        + diagram.getClass().getSimpleName() + "): " + problem.strip()
                        + "\n--- rejected source ---\n" + codePlantUml);
            }
        }
    }

    private static void drawingCheckEngine() {
        if (DrawingEngine.Usable) {
            return;
        }
        throw new IllegalStateException("Graphviz is not usable, and a class diagram cannot be "
            + "laid out without it: " + String.join(" / ", DrawingEngine.DIAGNOSTIC)
            + ". Install Graphviz (\"apt install graphviz\", \"brew install graphviz\"), or point "
            + "the GRAPHVIZ_DOT environment variable at the dot executable. Without this check "
            + "the generation would have written an image carrying an error message, and reported "
            + "it as a success.");
    }

    private static final class DrawingEngine {
        private static final ImmutableList<String> DIAGNOSTIC;
        private static final boolean Usable;

        static {
            List<String> lines = new ArrayList<>();

            int statut = GraphvizUtils.addDotStatus(lines, false);
            Usable = statut == 0;
            DIAGNOSTIC = ImmutableList.copyOf(lines);
        }

        private DrawingEngine() {}
    }
}

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

    private static final int COTE_MAXIMAL_PNG = 16384;

    private static final String DOSSIER_DES_VUES = "vues";

    private static final String NOM_DE_L_INTEGRALE = "projet";

    private PlantUmlWriter() {}

    public static ImmutableList<Path> writeViews(UmlProject project, Path dossier, String extension,
        UmlStyle style, boolean keepPrevious) throws IOException {
        checkNotNull(project);
        checkNotNull(dossier);
        checkNotNull(style);
        checkArgument(supporte("x" + extension),
            "The output format must be .png or .svg (got: %s)", extension);
        checkArgument(!project.views().isEmpty(),
            "This project describes no view; nothing would be written.");

        Path racine = destination(dossier, keepPrevious);
        Path desVues = racine.resolve(DOSSIER_DES_VUES);
        Files.createDirectories(desVues);

        ImmutableList.Builder<Path> ecrits = ImmutableList.builder();
        boolean integrale = true;

        Map<String, String> detailleePar = detailleePar(project, extension);

        for (UmlView vue : project.views()) {
            Path fichier = integrale
                ? racine.resolve(NOM_DE_L_INTEGRALE + extension)
                : desVues.resolve(vue.nomDeFichier() + extension);

            write(vue, project.diagram(), fichier.toString(), style,
                navigation(vue, detailleePar, integrale, extension));
            Proportions.verifier(fichier, vue.name());
            ecrits.add(fichier);
            integrale = false;
        }
        return ecrits.build();
    }

    private static Path destination(Path dossier, boolean keepPrevious) throws IOException {
        if (!keepPrevious) {
            viderLesVues(dossier);
            return dossier;
        }
        if (!Files.exists(dossier)) {
            return dossier;
        }
        for (int numero = 2; numero < 1000; numero++) {
            Path candidat = dossier.resolveSibling(dossier.getFileName() + "-" + numero);
            if (!Files.exists(candidat)) {
                return candidat;
            }
        }
        throw new IllegalStateException("Too many kept generations next to " + dossier
            + ". Remove the old ones, or drop keepPrevious.");
    }

    private static void viderLesVues(Path dossier) throws IOException {
        if (!Files.isDirectory(dossier)) {
            return;
        }
        for (Path racine : new Path[] {dossier, dossier.resolve(DOSSIER_DES_VUES)}) {
            if (!Files.isDirectory(racine)) {
                continue;
            }
            try (Stream<Path> fichiers = Files.list(racine)) {
                for (Path fichier : fichiers.sorted(Comparator.naturalOrder()).toList()) {
                    String nom = fichier.getFileName().toString();
                    if (Files.isRegularFile(fichier)
                        && (supporte(nom) || nom.endsWith(EXTENSION_PUML))) {
                        Files.delete(fichier);
                    }
                }
            }
        }
    }

    private static void write(UmlView vue, UmlDiagram reglages, String fichier, UmlStyle style,
        Map<String, String> navigation) throws IOException {
        UmlDiagram propres =
            new UmlDiagram(vue.name(), vue.direction(), reglages.hideEmptyMembers());

        ecrire(new PlantUmlSyntaxBuilder(style, propres).navigation(navigation),
            vue.entities(), fichier);
    }

    private static Map<String, String> detailleePar(UmlProject project, String extension) {
        Map<String, String> vers = new LinkedHashMap<>();
        boolean integrale = true;

        for (UmlView vue : project.views()) {
            if (integrale) {
                integrale = false;
                continue;
            }
            for (UmlEntity entite : vue.entities()) {
                vers.putIfAbsent(entite.qualifiedName(), vue.nomDeFichier() + extension);
            }
        }
        return vers;
    }

    private static Map<String, String> navigation(UmlView vue, Map<String, String> detailleePar,
        boolean integrale, String extension) {
        String propre = vue.nomDeFichier() + extension;
        Map<String, String> pourCetteVue = new LinkedHashMap<>();

        for (UmlEntity entite : vue.entities()) {
            String cible = detailleePar.get(entite.qualifiedName());
            if (cible == null || cible.equals(propre)) {
                continue;
            }
            pourCetteVue.put(entite.qualifiedName(),
                integrale ? DOSSIER_DES_VUES + "/" + cible : cible);
        }
        return pourCetteVue;
    }

    public static boolean supporte(String outputFileName) {
        checkNotNull(outputFileName);
        String normalise = outputFileName.toLowerCase(Locale.ROOT);
        return normalise.endsWith(EXTENSION_SVG) || normalise.endsWith(EXTENSION_PNG);
    }

    private static FileFormat formatPour(String outputFileName) {
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

        ecrire(new PlantUmlSyntaxBuilder(style, project.diagram()), project.entities(),
            outputFileName);

        Proportions.verifier(Path.of(outputFileName),
            project.diagram().aUnTitre() ? project.diagram().title() : outputFileName);
    }

    private static void ecrire(PlantUmlSyntaxBuilder syntax, Iterable<UmlEntity> entities,
        String outputFileName) throws IOException {
        String codePlantUml = syntax.document(entities).build();

        FileFormat format = formatPour(outputFileName);

        try (OutputStream os = new FileOutputStream(outputFileName)) {
            SourceStringReader reader = new SourceStringReader(codePlantUml);

            verifierAcceptation(reader, codePlantUml);
            verifierMoteurDeDessin();

            int point = outputFileName.lastIndexOf('.');
            Files.writeString(Path.of(outputFileName.substring(0, point) + EXTENSION_PUML),
                codePlantUml);

            GraphvizUtils.setLocalImageLimit(COTE_MAXIMAL_PNG);
            try {
                reader.outputImage(os, new FileFormatOption(format));
            } finally {
                GraphvizUtils.removeLocalLimitSize();
            }

        }
    }

    private static void verifierAcceptation(SourceStringReader reader, String codePlantUml) {
        for (BlockUml bloc : reader.getBlocks()) {
            Diagram diagramme = bloc.getDiagram();
            String probleme = diagramme.getWarningOrError();

            if (probleme != null) {
                throw new IllegalStateException(
                    "The generated PlantUML source was rejected by the rendering engine ("
                        + diagramme.getClass().getSimpleName() + "): " + probleme.strip()
                        + "\n--- rejected source ---\n" + codePlantUml);
            }
        }
    }

    private static void verifierMoteurDeDessin() {
        if (MoteurDeDessin.UTILISABLE) {
            return;
        }
        throw new IllegalStateException("Graphviz is not usable, and a class diagram cannot be "
            + "laid out without it: " + String.join(" / ", MoteurDeDessin.DIAGNOSTIC)
            + ". Install Graphviz (\"apt install graphviz\", \"brew install graphviz\"), or point "
            + "the GRAPHVIZ_DOT environment variable at the dot executable. Without this check "
            + "the generation would have written an image carrying an error message, and reported "
            + "it as a success.");
    }

    private static final class MoteurDeDessin {

        private static final ImmutableList<String> DIAGNOSTIC;
        private static final boolean UTILISABLE;

        static {
            List<String> lignes = new ArrayList<>();
            int statut = GraphvizUtils.addDotStatus(lignes, false);
            UTILISABLE = statut == 0;
            DIAGNOSTIC = ImmutableList.copyOf(lignes);
        }

        private MoteurDeDessin() {}
    }
}

package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;

/**
 * What the generation did, said in one go.
 *
 * @param classesRead the number of classes that entered the diagram.
 * @param classesIgnored the classes left out, for lack of &#64;UMLClass.
 * @param files the images written.
 * @param ignoredFile the file listing the ignored classes, if one was written.
 * @param warnings what the generation reported along the way.
 */
public record GenerationReport(int classesRead, ImmutableList<String> classesIgnored,
    ImmutableList<Path> files, Optional<Path> ignoredFile,
    ImmutableList<String> warnings) {
    /** Checks that nothing is null; an empty report is written with empty lists. */
    public GenerationReport {
        checkNotNull(classesIgnored);
        checkNotNull(files);
        checkNotNull(ignoredFile,
            "The ignored-classes file cannot be null (use Optional.empty()).");
        checkNotNull(warnings, "The list of warnings cannot be null (use an empty list).");
    }

    /**
     * A report with neither an ignored-classes file nor a warning.
     *
     * @param classesRead the number of classes that entered the diagram.
     * @param classesIgnored the classes left out, for lack of &#64;UMLClass.
     * @param files the images written.
     */
    public GenerationReport(int classesRead, ImmutableList<String> classesIgnored,
        ImmutableList<Path> files) {
        this(classesRead, classesIgnored, files, Optional.empty(), ImmutableList.of());
    }

    /**
     * {@return the report in plain words, as the command line and the Maven plugin *
     * l'affichent}
     */
    public String summary() {
        StringBuilder text = new StringBuilder();
        text.append(files.size()).append(" diagram(s) written");
        if (!files.isEmpty()) {
            Path target = files.size() == 1 ? files.get(0) : files.get(0).getParent();
            text.append(" to ").append(target == null ? files.get(0) : target);
        }
        text.append('\n').append("  ").append(classesRead)
             .append(" class(es) drawn, ").append(classesIgnored.size())
             .append(" ignored (no @UMLClass)");

        if (!classesIgnored.isEmpty() && classesIgnored.size() <= THRESHOLD_ENUMERATION) {
            text.append(" : ").append(String.join(", ", classesIgnored));
        }

        if (ignoredFile.isPresent()) {
            text.append('\n').append("  the full list of ignored classes is in ")
                 .append(ignoredFile.get());
        }
        return text.toString();
    }

    private static final int THRESHOLD_ENUMERATION = 12;
}

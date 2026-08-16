package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlProject;

public record ScanResult(UmlProject project, ImmutableList<String> classesIgnored) {
    public ScanResult {
        checkNotNull(project, "The scanned project cannot be null.");
        checkNotNull(classesIgnored,
            "The list of ignored classes cannot be null (use an empty list).");
    }
}

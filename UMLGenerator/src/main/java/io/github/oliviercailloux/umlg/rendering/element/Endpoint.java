package io.github.oliviercailloux.umlg.rendering.element;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

final class Endpoint {
    record Label(String written, String displayed) {
        boolean isEmpty() {
            return written.isEmpty();
        }
    }

    private Endpoint() {}

    static Label source(UmlAssociation association) {
        Chunks chunks = new Chunks();

        chunks.add(association.sourceRole(), "%s");
        chunks.add(association.sourceMultiplicity(), "%s");

        return chunks.assemble();
    }

    static Label target(UmlAssociation association) {
        Chunks chunks = new Chunks();

        chunks.add(association.targetRole(), association.derived() ? "/%s" : "%s");
        chunks.add(association.targetMultiplicity(), "%s");

        for (String rule : association.constraints()) {
            chunks.addAsIs(ConstraintNotation.one(rule), "{" + rule + "}");
        }
        return chunks.assemble();
    }

    private static final class Chunks {
        private final ImmutableList.Builder<String> written = ImmutableList.builder();
        private final ImmutableList.Builder<String> displayed = ImmutableList.builder();

        void add(String text, String template) {
            if (text.isEmpty()) {
                return;
            }
            addAsIs(template.formatted(PlantUmlEscaper.label(text)),
                template.formatted(text));
        }

        void addAsIs(String writtenText, String displayedText) {
            written.add(writtenText);
            displayed.add(displayedText);
        }

        Label assemble() {
            return new Label(String.join(" ", written.build()),
                String.join(" ", displayed.build()));
        }
    }
}

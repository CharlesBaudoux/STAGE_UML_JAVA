package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationClassExtractor;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

final class AssociationClassChecker {
    private final Reporter reporter;
    private final Target target;

    AssociationClassChecker(Reporter reporter, Target target) {
        this.reporter = reporter;
        this.target = target;
    }

    void check(TypeElement clazz) {
        AnnotationMirror annotation = reporter.mirror(clazz, UMLAssociationClass.class);
        if (annotation == null) {
            return;
        }
        String context = "@UMLAssociationClass on " + clazz.getQualifiedName();

        if (!reporter.requireBox(clazz, context,
            "the dashed line would reach an empty placed", clazz, annotation)) {
            return;
        }

        checkEndpoint(clazz, annotation, context, "source");
        checkEndpoint(clazz, annotation, context, "target");
    }

    private void checkEndpoint(TypeElement clazz, AnnotationMirror annotation,
        String context, String member) {
        String written = target.read(annotation, member);

        if (written != null && written.contentEquals(clazz.getQualifiedName())) {
            reporter.error(
                AssociationClassExtractor.messageReflexiveEndpoint(context, member + "()"),
                clazz, annotation, reporter.valueWritten(annotation, member));
            return;
        }
        target.resolve(annotation, member, context, clazz);
    }
}

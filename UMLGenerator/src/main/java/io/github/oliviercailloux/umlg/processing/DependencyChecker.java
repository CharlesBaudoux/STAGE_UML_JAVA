package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLDependencies;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.relation.DependencyExtractor;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

final class DependencyChecker {
    private final Reporter reporter;
    private final Target target;

    DependencyChecker(Reporter reporter, Target target) {
        this.reporter = reporter;
        this.target = target;
    }

    void check(TypeElement clazz) {
        List<AnnotationMirror> dependencies =
            reporter.repeated(clazz, UMLDependency.class, UMLDependencies.class);
        if (dependencies.isEmpty()) {
            return;
        }
        String location = clazz.getQualifiedName().toString();
        String context = "@UMLDependency on " + location;

        if (!reporter.requireBox(clazz, context,
            "this dependency would be drawn from nowhere", clazz, dependencies.get(0))) {
            return;
        }

        Set<UmlDependency> already = new HashSet<>();

        ImmutableSet<String> byAssociation = associationsTargets(clazz);

        for (int rank = 0; rank < dependencies.size(); rank++) {
            AnnotationMirror annotation = dependencies.get(rank);

            String dependencyContext = Reporter.repeatedContext("UMLDependency",
                location, rank, dependencies.size());
            String nameTarget =
                target.resolve(annotation, "target", dependencyContext, clazz);
            if (nameTarget == null) {
                continue;
            }

            if (nameTarget.contentEquals(clazz.getQualifiedName())) {
                reporter.error(
                    DependencyExtractor.messageReflexiveDependency(dependencyContext),
                    clazz, annotation, reporter.valueWritten(annotation, "target"));
                continue;
            }

            UmlDependency dependency;
            try {
                dependency = new UmlDependency(nameTarget, reporter.text(annotation, "label"));
            } catch (IllegalArgumentException e) {
                reporter.error(dependencyContext + " : " + e.getMessage(), clazz,
                    annotation);
                continue;
            }

            if (!already.add(dependency)) {
                reporter.error(DependencyExtractor.duplicateDependencyMessage(
                    dependencyContext, nameTarget), clazz, annotation);
                continue;
            }

            if (byAssociation.contains(nameTarget)) {
                reporter.warning(dependencyContext + ": an @UMLAssociation of this "
                    + "class already links it to " + nameTarget + ". A dependency says strictly "
                    + "less than an association, so the second line would only clutter the "
                    + "diagram.",
                    clazz, annotation, reporter.valueWritten(annotation, "target"));
            }
        }
    }

    private ImmutableSet<String> associationsTargets(TypeElement clazz) {
        ImmutableSet.Builder<String> targets = ImmutableSet.builder();
        for (Element member : clazz.getEnclosedElements()) {
            if (member.getKind() != ElementKind.FIELD) {
                continue;
            }
            AnnotationMirror association = reporter.mirror(member, UMLAssociation.class);
            if (association == null || !Notation.stroke(reporter, association)) {
                continue;
            }
            String name = target.read(association, "target");
            if (name != null) {
                targets.add(name);
            }
        }
        return targets.build();
    }
}

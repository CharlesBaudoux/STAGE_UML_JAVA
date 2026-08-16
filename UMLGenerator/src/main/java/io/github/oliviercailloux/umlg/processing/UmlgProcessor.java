package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLConstraints;
import io.github.oliviercailloux.umlg.annotation.UMLDependencies;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLNotes;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

public final class UmlgProcessor extends AbstractProcessor {
    private static final ImmutableList<Class<? extends Annotation>> Watched = ImmutableList.of(
        UMLClass.class,
        UMLAssociation.class,
        UMLDependency.class,
        UMLDependencies.class,
        UMLInclude.class,
        UMLExclude.class,
        UMLDerived.class,
        UMLMultiplicity.class,
        UMLNote.class,
        UMLNotes.class,
        UMLConstraint.class,
        UMLConstraints.class,
        UMLPackage.class,
        UMLAssociationClass.class);

    private static final String OPTION_WARNINGS = "umlg.warnings";

    private static final String SILENT_WARNINGS = "none";

    private static final String OPTION_RULES = "umlg.rules";

    private Reporter reporter;
    private ClassChecker classes;
    private AssociationChecker associations;
    private DependencyChecker dependencies;
    private MemberChecker members;
    private DerivedChecker derived;
    private MultiplicityChecker multiplicities;
    private NoteChecker notes;
    private ConstraintChecker constraints;
    private PackageChecker groups;
    private AssociationClassChecker associationClasses;

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);

        Map<String, String> options = environment.getOptions();
        String requested = options.get(OPTION_WARNINGS);
        boolean warn = !SILENT_WARNINGS.equals(requested);

        reporter = new Reporter(environment.getMessager(), environment.getElementUtils(),
            warn);

        if (options.containsKey(OPTION_WARNINGS)
            && !SILENT_WARNINGS.equals(requested)) {
            String written = requested == null
                ? "-A" + OPTION_WARNINGS + ": no value"
                : "-A" + OPTION_WARNINGS + "=" + requested + ": unknown value";
            reporter.error(written + ". The only one this processor understands is \""
                + SILENT_WARNINGS + "\", which silences its warnings; write -A"
                + OPTION_WARNINGS + "=" + SILENT_WARNINGS
                + ", or remove the option to keep them.");
        }

        Strategy strategy = new Strategy(readRules(options.get(OPTION_RULES)));
        Target target = new Target(reporter, environment.getTypeUtils());
        AttributeType typeAttribute = new AttributeType(environment.getTypeUtils(),
            environment.getElementUtils());

        classes = new ClassChecker(reporter);
        associations = new AssociationChecker(reporter, target, typeAttribute);
        dependencies = new DependencyChecker(reporter, target);
        members = new MemberChecker(reporter, strategy);
        derived = new DerivedChecker(reporter, strategy);
        multiplicities = new MultiplicityChecker(reporter, strategy);
        notes = new NoteChecker(reporter, strategy);
        constraints = new ConstraintChecker(reporter, strategy);
        groups = new PackageChecker(reporter);
        associationClasses = new AssociationClassChecker(reporter, target);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Watched.stream().map(Class::getCanonicalName)
            .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public Set<String> getSupportedOptions() {
        return ImmutableSet.of(OPTION_WARNINGS, OPTION_RULES);
    }

    private Rules readRules(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path file = Paths.get(path);

        if (!Files.isRegularFile(file)) {
            reporter.configurationWarning("-A" + OPTION_RULES + "=" + path
                + ": no such file; looked for " + file.toAbsolutePath()
                + (file.isAbsolute() ? ""
                    : " (a relative path is resolved against the compiler's working directory, "
                        + "not the project directory)")
                + ". The checks that depend on the project strategy cannot conclude, and will "
                + "stay silent.");
            return null;
        }
        try {
            return Rules.file(file);
        } catch (IllegalArgumentException e) {
            reporter.error(path + " : " + e.getMessage());
            return null;
        } catch (IOException e) {
            reporter.configurationWarning("-A" + OPTION_RULES + "=" + path
                + ": cannot be read (" + e.getMessage() + "). The checks that depend on the "
                + "project strategy will stay silent.");
            return null;
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment turn) {
        if (turn.processingOver()) {
            return false;
        }

        for (Element clazz : holders(turn, UMLClass.class, UMLDependency.class,
            UMLDependencies.class, UMLPackage.class, UMLAssociationClass.class)) {
            classes.check((TypeElement) clazz);
            dependencies.check((TypeElement) clazz);
            groups.check((TypeElement) clazz);
            associationClasses.check((TypeElement) clazz);
        }

        for (Element field : holders(turn, UMLAssociation.class)) {
            associations.check(field);
        }

        for (Element member : holders(turn, UMLInclude.class, UMLExclude.class)) {
            members.check(member);
        }

        for (Element member : holders(turn, UMLDerived.class)) {
            derived.check(member);
        }

        for (Element member : holders(turn, UMLMultiplicity.class)) {
            multiplicities.check(member);
        }

        for (Element holder : holders(turn, UMLNote.class, UMLNotes.class)) {
            notes.check(holder);
        }

        for (Element holder : holders(turn, UMLConstraint.class, UMLConstraints.class)) {
            constraints.check(holder);
        }

        return false;
    }

    @SafeVarargs
    private static ImmutableSet<Element> holders(RoundEnvironment turn,
        Class<? extends Annotation>... annotations) {
        ImmutableSet.Builder<Element> holders = ImmutableSet.builder();
        for (Class<? extends Annotation> annotation : annotations) {
            holders.addAll(turn.getElementsAnnotatedWith(annotation));
        }
        return sansLesClonesDeComposants(holders.build());
    }

    private static ImmutableSet<Element> sansLesClonesDeComposants(ImmutableSet<Element> all) {
        ImmutableSet<String> componentFields = all.stream()
            .filter(element -> element.getKind() == ElementKind.FIELD)
            .filter(Member::comesFromComponent)
            .map(Member::location)
            .collect(ImmutableSet.toImmutableSet());

        if (componentFields.isEmpty()) {
            return all;
        }
        return all.stream()
            .filter(element -> element.getKind() != ElementKind.METHOD
                || !Member.comesFromComponent(element)
                || !componentFields.contains(Member.location(element)))
            .collect(ImmutableSet.toImmutableSet());
    }
}

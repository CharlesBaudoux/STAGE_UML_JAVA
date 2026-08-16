package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationClassExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.DependencyExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.InheritanceExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.RelationExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.StringJoiner;

public final class EntityExtractor {
    private static final ImmutableList<RelationExtractor> Extractors = ImmutableList.of(
        new AssociationExtractor(),
        new DependencyExtractor(),
        new InheritanceExtractor(),
        new AssociationClassExtractor());

    private EntityExtractor() {}

    public static UmlEntity extract(Class<?> clazz) {
        return extract(clazz, Rules.no());
    }

    public static UmlEntity extract(Class<?> clazz, Rules rules) {
        checkNotNull(clazz);
        checkNotNull(rules);

        checkArgument(clazz.isAnnotationPresent(UMLClass.class),
            "Class %s does not carry the @UMLClass annotation.", clazz.getName());

        UMLClass annotation = clazz.getAnnotation(UMLClass.class);

        String finalName = annotation.name().isEmpty() ? clazz.getSimpleName() : annotation.name();
        String finalStereotype = annotation.stereotype();
        String finalColor = annotation.color();

        UMLVisibility finalStrategy = rules.strategy(annotation.strategy());

        ClassHeaderValidator.validateName(finalName, clazz);
        ClassHeaderValidator.validateStereotype(finalStereotype, clazz);
        ClassHeaderValidator.validateColor(finalColor, clazz);

        ImmutableList<UmlField> fields = ImmutableList.<UmlField>builder()
            .addAll(MemberExtractor.extractFields(clazz, finalStrategy, rules))
            .addAll(MemberExtractor.methodsAsAttributes(clazz, finalStrategy, rules))
            .build();
        ImmutableList<UmlMethod> methods =
            MemberExtractor.extractMethods(clazz, finalStrategy, rules);

        UmlEntity.Builder builder = UmlEntity.builder(clazz.getName(), finalName)
            .kind(kind(clazz))
            .stereotype(finalStereotype)
            .color(finalColor)
            .strategy(finalStrategy)

            .literals(MemberExtractor.extractLiterals(clazz, finalStrategy, rules))
            .fields(fields)
            .methods(methods)
            .notes(NoteExtractor.extract(clazz, fields, methods, rules))
            .constraints(ConstraintExtractor.of(clazz, clazz.getName()))
            .group(PackageExtractor.extract(clazz))
            .typeParameters(typeParameters(clazz, rules));

        for (RelationExtractor extractor : Extractors) {
            builder.relations(extractor.extract(clazz, rules));
        }
        return builder.build();
    }

    private static UmlEntityKind kind(Class<?> clazz) {
        if (clazz.isAnnotation()) {
            return UmlEntityKind.ANNOTATION;
        }
        if (clazz.isInterface()) {
            return UmlEntityKind.INTERFACE;
        }
        if (clazz.isEnum()) {
            return UmlEntityKind.ENUM;
        }
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return UmlEntityKind.ABSTRACT_CLASS;
        }
        return UmlEntityKind.CLASS;
    }

    private static String typeParameters(Class<?> clazz, Rules rules) {
        TypeVariable<?>[] variables = clazz.getTypeParameters();
        if (variables.length == 0) {
            return "";
        }
        StringJoiner parameters = new StringJoiner(", ", "<", ">");
        for (TypeVariable<?> variable : variables) {
            StringBuilder written = new StringBuilder(variable.getName());

            StringJoiner bounds = new StringJoiner(" & ");
            for (Type bound : variable.getBounds()) {
                if (bound != Object.class) {
                    bounds.add(DisplayedType.of(bound, rules));
                }
            }
            if (bounds.length() > 0) {
                written.append(" extends ").append(bounds);
            }
            parameters.add(written);
        }
        return parameters.toString();
    }
}

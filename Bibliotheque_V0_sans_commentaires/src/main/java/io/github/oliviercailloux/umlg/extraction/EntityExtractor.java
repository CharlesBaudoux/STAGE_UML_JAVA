package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.DependencyExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.InheritanceExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.RelationExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.lang.reflect.Modifier;

// Traduit une classe annotée en une UmlEntity : c'est le chef d'orchestre de l'extraction.
//
// Il ne fait presque rien lui-même. Il lit l'en-tête (@UMLClass), puis délègue : les membres à
// MemberExtractor, les relations aux RelationExtractor, la validation à ClassHeaderValidator.
public final class EntityExtractor {

    private static final ImmutableList<RelationExtractor> EXTRACTEURS = ImmutableList.of(
        new AssociationExtractor(),
        new DependencyExtractor(),
        new InheritanceExtractor());

    private EntityExtractor() {}

    public static UmlEntity extract(Class<?> clazz) {
        checkNotNull(clazz);

        checkArgument(clazz.isAnnotationPresent(UMLClass.class),
            "Class %s does not carry the @UMLClass annotation.", clazz.getName());

        UMLClass annotation = clazz.getAnnotation(UMLClass.class);

        String finalName = annotation.name().isEmpty() ? clazz.getSimpleName() : annotation.name();
        String finalStereotype = annotation.stereotype();
        String finalColor = annotation.color();
        UMLVisibility finalStrategy = annotation.strategy();

        ClassHeaderValidator.validateName(finalName, clazz);
        ClassHeaderValidator.validateStereotype(finalStereotype, clazz);
        ClassHeaderValidator.validateColor(finalColor, clazz);

        ImmutableList<UmlField> fields = MemberExtractor.extractFields(clazz, finalStrategy);
        ImmutableList<UmlMethod> methods = MemberExtractor.extractMethods(clazz, finalStrategy);

        UmlEntity.Builder builder = UmlEntity.builder(clazz.getName(), finalName)
            .kind(kind(clazz))
            .stereotype(finalStereotype)
            .color(finalColor)
            .strategy(finalStrategy)

            .literals(MemberExtractor.extractLiterals(clazz, finalStrategy))
            .fields(fields)
            .methods(methods)
            .notes(NoteExtractor.extraire(clazz, fields, methods))
            .constraints(ConstraintExtractor.de(clazz, clazz.getName()));

        for (RelationExtractor extracteur : EXTRACTEURS) {
            builder.relations(extracteur.extraire(clazz));
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
}

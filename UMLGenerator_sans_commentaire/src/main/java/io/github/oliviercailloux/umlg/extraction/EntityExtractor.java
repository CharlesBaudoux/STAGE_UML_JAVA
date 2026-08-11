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
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.StringJoiner;

public final class EntityExtractor {

    private static final ImmutableList<RelationExtractor> EXTRACTEURS = ImmutableList.of(
        new AssociationExtractor(),
        new DependencyExtractor(),
        new InheritanceExtractor(),
        new AssociationClassExtractor());

    private EntityExtractor() {}

    public static UmlEntity extract(Class<?> clazz) {
        return extract(clazz, Regles.aucune());
    }

    public static UmlEntity extract(Class<?> clazz, Regles regles) {
        checkNotNull(clazz);
        checkNotNull(regles);

        checkArgument(clazz.isAnnotationPresent(UMLClass.class),
            "Class %s does not carry the @UMLClass annotation.", clazz.getName());

        UMLClass annotation = clazz.getAnnotation(UMLClass.class);

        String finalName = annotation.name().isEmpty() ? clazz.getSimpleName() : annotation.name();
        String finalStereotype = annotation.stereotype();
        String finalColor = annotation.color();
        UMLVisibility finalStrategy = regles.strategieDe(annotation.strategy());

        ClassHeaderValidator.validateName(finalName, clazz);
        ClassHeaderValidator.validateStereotype(finalStereotype, clazz);
        ClassHeaderValidator.validateColor(finalColor, clazz);

        ImmutableList<UmlField> fields = ImmutableList.<UmlField>builder()
            .addAll(MemberExtractor.extractFields(clazz, finalStrategy, regles))
            .addAll(MemberExtractor.methodesEnAttributs(clazz, regles))
            .build();
        ImmutableList<UmlMethod> methods =
            MemberExtractor.extractMethods(clazz, finalStrategy, regles);

        UmlEntity.Builder builder = UmlEntity.builder(clazz.getName(), finalName)
            .kind(kind(clazz))
            .stereotype(finalStereotype)
            .color(finalColor)
            .strategy(finalStrategy)

            .literals(MemberExtractor.extractLiterals(clazz, finalStrategy))
            .fields(fields)
            .methods(methods)
            .notes(NoteExtractor.extraire(clazz, fields, methods))
            .constraints(ConstraintExtractor.de(clazz, clazz.getName()))
            .group(PackageExtractor.extraire(clazz, regles))
            .typeParameters(parametresDeType(clazz, regles));

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

    private static String parametresDeType(Class<?> clazz, Regles regles) {
        TypeVariable<?>[] variables = clazz.getTypeParameters();
        if (variables.length == 0) {
            return "";
        }
        StringJoiner parametres = new StringJoiner(", ", "<", ">");
        for (TypeVariable<?> variable : variables) {
            StringBuilder ecrit = new StringBuilder(variable.getName());
            StringJoiner bornes = new StringJoiner(" & ");
            for (Type borne : variable.getBounds()) {
                if (borne != Object.class) {
                    bornes.add(TypeAffiche.de(borne, regles));
                }
            }
            if (bornes.length() > 0) {
                ecrit.append(" extends ").append(bornes);
            }
            parametres.add(ecrit);
        }
        return parametres.toString();
    }
}

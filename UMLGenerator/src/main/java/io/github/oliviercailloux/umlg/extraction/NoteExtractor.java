package io.github.oliviercailloux.umlg.extraction;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

final class NoteExtractor {
    private NoteExtractor() {}

    static ImmutableList<UmlNote> extract(Class<?> clazz, List<UmlField> fields,
        List<UmlMethod> methods) {
        return extract(clazz, fields, methods, Rules.no());
    }

    static ImmutableList<UmlNote> extract(Class<?> clazz, List<UmlField> fields,
        List<UmlMethod> methods, Rules rules) {
        ImmutableList.Builder<UmlNote> result = ImmutableList.builder();

        for (UMLNote annotation : clazz.getAnnotationsByType(UMLNote.class)) {
            result.add(translate(annotation, "", clazz.getName()));
        }

        ImmutableSet<String> fieldsVisible =
            fields.stream().map(UmlField::name).collect(ImmutableSet.toImmutableSet());
        ImmutableSet<String> methodsVisible =
            methods.stream().map(UmlMethod::name).collect(ImmutableSet.toImmutableSet());

        for (Field field : OrderedMembers.fields(clazz, rules)) {
            String displayed = MemberExtractor.nameDisplayed(field);

            if (field.isSynthetic() || !fieldsVisible.contains(displayed)) {
                continue;
            }
            for (UMLNote annotation : field.getAnnotationsByType(UMLNote.class)) {
                result.add(translate(annotation, displayed,
                    clazz.getName() + "." + field.getName()));
            }
        }

        for (Method method : OrderedMembers.methods(clazz, rules)) {
            String displayed = MemberExtractor.nameDisplayed(method);

            if (method.isSynthetic()
                || !(methodsVisible.contains(displayed) || fieldsVisible.contains(displayed))) {
                continue;
            }
            for (UMLNote annotation : method.getAnnotationsByType(UMLNote.class)) {
                result.add(translate(annotation, displayed,
                    clazz.getName() + "." + method.getName()));
            }
        }
        return result.build();
    }

    private static UmlNote translate(UMLNote annotation, String anchor, String location) {
        try {
            return new UmlNote(annotation.text(), annotation.direction(), anchor);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                "@UMLNote on " + location + " : " + e.getMessage(), e);
        }
    }
}

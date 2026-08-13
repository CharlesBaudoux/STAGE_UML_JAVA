package io.github.oliviercailloux.umlg.extraction;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

final class NoteExtractor {

    private NoteExtractor() {}

    static ImmutableList<UmlNote> extraire(Class<?> clazz, List<UmlField> fields,
        List<UmlMethod> methods) {
        return extraire(clazz, fields, methods, Regles.aucune());
    }

    static ImmutableList<UmlNote> extraire(Class<?> clazz, List<UmlField> fields,
        List<UmlMethod> methods, Regles regles) {

        ImmutableList.Builder<UmlNote> result = ImmutableList.builder();

        for (UMLNote annotation : clazz.getAnnotationsByType(UMLNote.class)) {
            result.add(traduire(annotation, "", clazz.getName()));
        }

        ImmutableSet<String> champsVisibles =
            fields.stream().map(UmlField::name).collect(ImmutableSet.toImmutableSet());
        ImmutableSet<String> methodesVisibles =
            methods.stream().map(UmlMethod::name).collect(ImmutableSet.toImmutableSet());

        for (Field field : MembresOrdonnes.champs(clazz, regles)) {
            String affiche = MemberExtractor.nomAffiche(field);

            if (field.isSynthetic() || !champsVisibles.contains(affiche)) {
                continue;
            }
            for (UMLNote annotation : field.getAnnotationsByType(UMLNote.class)) {
                result.add(traduire(annotation, affiche,
                    clazz.getName() + "." + field.getName()));
            }
        }

        for (Method method : MembresOrdonnes.methodes(clazz, regles)) {
            String affiche = MemberExtractor.nomAffiche(method);

            if (method.isSynthetic()
                || !(methodesVisibles.contains(affiche) || champsVisibles.contains(affiche))) {
                continue;
            }
            for (UMLNote annotation : method.getAnnotationsByType(UMLNote.class)) {
                result.add(traduire(annotation, affiche,
                    clazz.getName() + "." + method.getName()));
            }
        }
        return result.build();
    }

    private static UmlNote traduire(UMLNote annotation, String ancre, String emplacement) {
        try {
            return new UmlNote(annotation.text(), annotation.direction(), ancre);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                "@UMLNote sur " + emplacement + " : " + e.getMessage(), e);
        }
    }
}

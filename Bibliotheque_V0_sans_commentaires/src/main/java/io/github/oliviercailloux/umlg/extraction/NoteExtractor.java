package io.github.oliviercailloux.umlg.extraction;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

// Rassemble les bulles de commentaire d'une classe : les siennes, et celles de ses membres.
//
// Une note ne se dessine qu'accrochée à quelque chose. Cet extracteur reçoit donc les membres
// déjà retenus par MemberExtractor, et écarte les notes qui viseraient un membre absent de la
// boîte — un privé sous une stratégie PUBLIC, un getter écarté par le filtre anti-bruit. Sans ce
// tri, le rendu demanderait à PlantUML d'accrocher une bulle à un membre qu'il ne connaît pas.
//
// L'utilisateur, lui, n'a pas à le deviner : le processeur d'annotations l'avertit à la
// compilation, là où la note est écrite.
final class NoteExtractor {

    private NoteExtractor() {}

    static ImmutableList<UmlNote> extraire(Class<?> clazz, List<UmlField> fields,
        List<UmlMethod> methods) {

        ImmutableList.Builder<UmlNote> result = ImmutableList.builder();

        for (UMLNote annotation : clazz.getAnnotationsByType(UMLNote.class)) {
            result.add(traduire(annotation, "", clazz.getName()));
        }

        ImmutableSet<String> champsVisibles =
            fields.stream().map(UmlField::name).collect(ImmutableSet.toImmutableSet());
        ImmutableSet<String> methodesVisibles =
            methods.stream().map(UmlMethod::name).collect(ImmutableSet.toImmutableSet());

        for (Field field : MembresOrdonnes.champs(clazz)) {
            if (field.isSynthetic() || !champsVisibles.contains(field.getName())) {
                continue;
            }
            for (UMLNote annotation : field.getAnnotationsByType(UMLNote.class)) {
                result.add(traduire(annotation, field.getName(),
                    clazz.getName() + "." + field.getName()));
            }
        }

        for (Method method : MembresOrdonnes.methodes(clazz)) {
            if (method.isSynthetic() || !methodesVisibles.contains(method.getName())) {
                continue;
            }
            for (UMLNote annotation : method.getAnnotationsByType(UMLNote.class)) {
                result.add(traduire(annotation, method.getName(),
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

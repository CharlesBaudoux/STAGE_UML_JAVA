package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.extraction.TypeAffiche;
import java.lang.reflect.Field;

public final class CibleDeduite {

    private CibleDeduite() {}

    public static boolean absente(UMLAssociation association) {
        return association.target() == void.class;
    }

    public static Class<?> de(Field field, UMLAssociation association, String contexte) {
        if (!absente(association)) {
            return association.target();
        }
        Class<?> element = TypeAffiche.elementSeul(field.getGenericType());
        if (element != null) {
            return element;
        }
        Class<?> declare = field.getType();
        checkArgument(!declare.isPrimitive() && !declare.isArray(),
            "%s: target() is not written, and the field's type (%s) does not name a class of the "
                + "diagram. Write target explicitly.",
            contexte, declare.getSimpleName());

        checkArgument(!Iterable.class.isAssignableFrom(declare),
            "%s: target() is not written, and the field's type (%s) does not say what it "
                + "contains. Write target explicitly, or give the collection a type parameter.",
            contexte, declare.getSimpleName());

        return declare;
    }
}

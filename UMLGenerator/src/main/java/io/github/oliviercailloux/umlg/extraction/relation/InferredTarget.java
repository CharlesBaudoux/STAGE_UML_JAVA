package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.extraction.DisplayedType;
import java.lang.reflect.Field;

public final class InferredTarget {
    private InferredTarget() {}

    public static boolean missing(UMLAssociation association) {
        return association.target() == void.class;
    }

    public static Class<?> of(Field field, UMLAssociation association, String context) {
        if (!missing(association)) {
            return association.target();
        }
        Class<?> element = DisplayedType.loneElement(field.getGenericType());
        if (element != null) {
            return element;
        }

        Class<?> declare = field.getType();
        checkArgument(!declare.isPrimitive() && !declare.isArray(),
            messageTargetUninferable(context, declare.getSimpleName()));

        checkArgument(!Iterable.class.isAssignableFrom(declare),
            messageContentUninferable(context, declare.getSimpleName()));

        return declare;
    }

    public static String messageTargetUninferable(String context, String type) {
        return context + ": target() is not written, and the field's type (" + type + ") does "
            + "not name a class of the diagram. Write target explicitly.";
    }

    public static String messageContentUninferable(String context, String type) {
        return context + ": target() is not written, and the field's type (" + type + ") does "
            + "not say what it contains. Write target explicitly, or give the collection a type "
            + "parameter.";
    }
}

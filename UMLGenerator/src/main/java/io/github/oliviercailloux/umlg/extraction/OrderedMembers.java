package io.github.oliviercailloux.umlg.extraction;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.warning.Warner;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

public final class OrderedMembers {
    private static final Comparator<Method> BY_SIGNATURE =
        Comparator.comparing(Method::getName).thenComparing(OrderedMembers::parameters);

    private static final Set<Class<?>> LOST_ORDER_REPORTED =
        Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    private OrderedMembers() {}

    public static ImmutableList<Field> fields(Class<?> clazz, Rules rules) {
        if (rules.orderDeclaration()) {
            ImmutableList<String> order = ClassFileOrder.fields(clazz);
            if (order != null) {
                return Arrays.stream(clazz.getDeclaredFields())
                    .sorted(Comparator.comparingInt(field -> rank(order, field.getName())))
                    .collect(ImmutableList.toImmutableList());
            }
            reportLostOrder(clazz);
        }
        return Arrays.stream(clazz.getDeclaredFields())
            .sorted(Comparator.comparing(Field::getName))
            .collect(ImmutableList.toImmutableList());
    }

    public static ImmutableList<Method> methods(Class<?> clazz, Rules rules) {
        if (rules.orderDeclaration()) {
            ImmutableList<String> order = ClassFileOrder.methods(clazz);
            if (order != null) {
                return Arrays.stream(clazz.getDeclaredMethods())
                    .sorted(Comparator.comparingInt(
                        method -> rank(order, ClassFileOrder.key(method))))
                    .collect(ImmutableList.toImmutableList());
            }
            reportLostOrder(clazz);
        }
        return Arrays.stream(clazz.getDeclaredMethods())
            .sorted(BY_SIGNATURE)
            .collect(ImmutableList.toImmutableList());
    }

    private static int rank(ImmutableList<String> order, String key) {
        int rank = order.indexOf(key);
        return rank < 0 ? Integer.MAX_VALUE : rank;
    }

    private static void reportLostOrder(Class<?> clazz) {
        if (!LOST_ORDER_REPORTED.add(clazz)) {
            return;
        }
        Warner.say("\"order\": \"declaration\" is asked for, but the class file of "
            + clazz.getName() + " cannot be re-read; the members of this box fall back to "
            + "alphabetical order.");
    }

    private static String parameters(Method method) {
        return Arrays.stream(method.getParameterTypes())
            .map(Class::getName)
            .collect(Collectors.joining(","));
    }
}

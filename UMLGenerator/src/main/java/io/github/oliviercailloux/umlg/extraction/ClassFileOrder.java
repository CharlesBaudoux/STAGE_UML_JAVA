package io.github.oliviercailloux.umlg.extraction;

import com.google.common.collect.ImmutableList;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

final class ClassFileOrder {
    private static final Map<Class<?>, Optional<Members>> Reads =
        Collections.synchronizedMap(new WeakHashMap<>());

    private ClassFileOrder() {}

    static ImmutableList<String> fields(Class<?> clazz) {
        return members(clazz).map(Members::fields).orElse(null);
    }

    static ImmutableList<String> methods(Class<?> clazz) {
        return members(clazz).map(Members::methods).orElse(null);
    }

    private static Optional<Members> members(Class<?> clazz) {
        return Reads.computeIfAbsent(clazz, c -> Optional.ofNullable(read(c)));
    }

    static String key(Method method) {
        StringBuilder descriptor = new StringBuilder(method.getName()).append('(');
        for (Class<?> parameter : method.getParameterTypes()) {
            descriptor.append(descriptor(parameter));
        }
        return descriptor.append(')').append(descriptor(method.getReturnType())).toString();
    }

    private static String descriptor(Class<?> type) {
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        if (type.isPrimitive()) {
            return switch (type.getName()) {
                case "void" -> "V";
                case "boolean" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "short" -> "S";
                case "int" -> "I";
                case "long" -> "J";
                case "float" -> "F";
                case "double" -> "D";

                default -> throw new IllegalStateException(type.getName());
            };
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private record Members(ImmutableList<String> fields, ImmutableList<String> methods) {}

    private static Members read(Class<?> clazz) {
        String resource = "/" + clazz.getName().replace('.', '/') + ".class";

        try (InputStream bytes = clazz.getResourceAsStream(resource)) {
            if (bytes == null) {
                return null;
            }
            return read(new DataInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Members read(DataInputStream entry) throws IOException {
        entry.readInt();
        entry.readInt();

        int size = entry.readUnsignedShort();
        String[] strings = new String[size];
        for (int i = 1; i < size; i++) {
            int nature = entry.readUnsignedByte();
            switch (nature) {
                case 1 -> strings[i] = entry.readUTF();

                case 7, 8, 16, 19, 20 -> skip(entry, 2);

                case 15 -> skip(entry, 3);

                case 3, 4, 9, 10, 11, 12, 17, 18 -> skip(entry, 4);

                case 5, 6 -> {
                    skip(entry, 8);
                    i++;
                }
                default -> throw new IOException("unknown constant pool tag " + nature);
            }
        }

        skip(entry, 2);
        skip(entry, 4);
        skip(entry, 2 * entry.readUnsignedShort());

        ImmutableList.Builder<String> fields = ImmutableList.builder();
        int fieldsCount = entry.readUnsignedShort();
        for (int i = 0; i < fieldsCount; i++) {
            skip(entry, 2);
            fields.add(strings[entry.readUnsignedShort()]);
            skip(entry, 2);
            skipAttributes(entry);
        }

        ImmutableList.Builder<String> methods = ImmutableList.builder();
        int methodsCount = entry.readUnsignedShort();
        for (int i = 0; i < methodsCount; i++) {
            skip(entry, 2);
            String name = strings[entry.readUnsignedShort()];
            methods.add(name + strings[entry.readUnsignedShort()]);
            skipAttributes(entry);
        }
        return new Members(fields.build(), methods.build());
    }

    private static void skipAttributes(DataInputStream entry) throws IOException {
        int count = entry.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            skip(entry, 2);
            skip(entry, entry.readInt());
        }
    }

    private static void skip(DataInputStream entry, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int skipped = entry.skipBytes(remaining);
            if (skipped <= 0) {
                if (entry.read() < 0) {
                    throw new IOException("truncated class file");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}

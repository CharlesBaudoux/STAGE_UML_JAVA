package io.github.oliviercailloux.umlg.extraction;

import com.google.common.collect.ImmutableList;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

final class OrdreDuFichierClasse {

    private OrdreDuFichierClasse() {}

    static ImmutableList<String> champs(Class<?> clazz) {
        Membres membres = lire(clazz);
        return membres == null ? null : membres.champs;
    }

    static ImmutableList<String> methodes(Class<?> clazz) {
        Membres membres = lire(clazz);
        return membres == null ? null : membres.methodes;
    }

    static String cle(Method methode) {
        StringBuilder descripteur = new StringBuilder(methode.getName()).append('(');
        for (Class<?> parametre : methode.getParameterTypes()) {
            descripteur.append(descripteur(parametre));
        }
        return descripteur.append(')').append(descripteur(methode.getReturnType())).toString();
    }

    private static String descripteur(Class<?> type) {
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

    private record Membres(ImmutableList<String> champs, ImmutableList<String> methodes) {}

    private static Membres lire(Class<?> clazz) {
        String ressource = "/" + clazz.getName().replace('.', '/') + ".class";

        try (InputStream octets = clazz.getResourceAsStream(ressource)) {
            if (octets == null) {
                return null;
            }
            return lire(new DataInputStream(octets));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Membres lire(DataInputStream entree) throws IOException {
        entree.readInt();
        entree.readInt();

        int taille = entree.readUnsignedShort();
        String[] chaines = new String[taille];
        for (int i = 1; i < taille; i++) {
            int nature = entree.readUnsignedByte();
            switch (nature) {
                case 1 -> chaines[i] = entree.readUTF();
                case 7, 8, 16, 19, 20 -> sauter(entree, 2);
                case 15 -> sauter(entree, 3);
                case 3, 4, 9, 10, 11, 12, 17, 18 -> sauter(entree, 4);
                case 5, 6 -> {
                    sauter(entree, 8);
                    i++;
                }
                default -> throw new IOException("unknown constant pool tag " + nature);
            }
        }

        sauter(entree, 2);
        sauter(entree, 4);
        sauter(entree, 2 * entree.readUnsignedShort());

        ImmutableList.Builder<String> champs = ImmutableList.builder();
        int nombreDeChamps = entree.readUnsignedShort();
        for (int i = 0; i < nombreDeChamps; i++) {
            sauter(entree, 2);
            champs.add(chaines[entree.readUnsignedShort()]);
            sauter(entree, 2);
            sauterAttributs(entree);
        }

        ImmutableList.Builder<String> methodes = ImmutableList.builder();
        int nombreDeMethodes = entree.readUnsignedShort();
        for (int i = 0; i < nombreDeMethodes; i++) {
            sauter(entree, 2);
            String nom = chaines[entree.readUnsignedShort()];
            methodes.add(nom + chaines[entree.readUnsignedShort()]);
            sauterAttributs(entree);
        }
        return new Membres(champs.build(), methodes.build());
    }

    private static void sauterAttributs(DataInputStream entree) throws IOException {
        int nombre = entree.readUnsignedShort();
        for (int i = 0; i < nombre; i++) {
            sauter(entree, 2);
            sauter(entree, entree.readInt());
        }
    }

    private static void sauter(DataInputStream entree, int octets) throws IOException {
        int restants = octets;
        while (restants > 0) {
            int sautes = entree.skipBytes(restants);
            if (sautes <= 0) {
                if (entree.read() < 0) {
                    throw new IOException("truncated class file");
                }
                sautes = 1;
            }
            restants -= sautes;
        }
    }
}

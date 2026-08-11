package io.github.oliviercailloux.umlg.extraction;

import com.google.common.collect.ImmutableList;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

// Lit dans le fichier .class l'ordre où les membres ont été déclarés.
//
// POURQUOI LIRE LE FICHIER, ET NON LA RÉFLEXION
//
// La réflexion ne connaît pas l'ordre de déclaration : getDeclaredFields et getDeclaredMethods
// rendent les membres « dans un ordre non spécifié », et il varie réellement d'une exécution à
// l'autre (voir MembresOrdonnes). Le fichier .class, lui, range ses tables de champs et de
// méthodes dans un ordre fixe — celui où javac les a écrites, qui est celui du source. Ce n'est
// pas une garantie de la spécification, mais c'est le comportement des compilateurs en usage, et
// surtout c'est un ordre reproductible : le fichier ne change pas entre deux lectures.
//
// CE QUI EST LU, ET RIEN D'AUTRE
//
// Les noms des champs et les signatures des méthodes, dans l'ordre des tables. Le réservoir de
// constantes est traversé parce que le format l'impose — les noms y sont rangés — mais rien
// n'en est interprété au-delà des chaînes UTF-8. Tout le reste (attributs, code) est sauté.
//
// Les méthodes sont désignées par « nom + descripteur » et non par leur seul nom : deux
// surcharges partagent un nom, et seul le descripteur — la forme JVM de la signature — les
// départage.
final class OrdreDuFichierClasse {

    private OrdreDuFichierClasse() {}

    // Les noms des champs, dans l'ordre du fichier ; null si le fichier est introuvable ou
    // illisible — l'appelant retombe alors sur l'ordre alphabétique, en le disant.
    static ImmutableList<String> champs(Class<?> clazz) {
        Membres membres = lire(clazz);
        return membres == null ? null : membres.champs;
    }

    // Les « nom + descripteur » des méthodes, dans l'ordre du fichier ; null en cas d'échec.
    static ImmutableList<String> methodes(Class<?> clazz) {
        Membres membres = lire(clazz);
        return membres == null ? null : membres.methodes;
    }

    // La clé d'une méthode de la réflexion, sous la même forme que celle lue dans le fichier.
    static String cle(Method methode) {
        StringBuilder descripteur = new StringBuilder(methode.getName()).append('(');
        for (Class<?> parametre : methode.getParameterTypes()) {
            descripteur.append(descripteur(parametre));
        }
        return descripteur.append(')').append(descripteur(methode.getReturnType())).toString();
    }

    // Le descripteur JVM d'un type : « I » pour int, « Ljava/lang/String; » pour String,
    // « [I » pour int[].
    private static String descripteur(Class<?> type) {
        if (type.isArray()) {
            // getName rend déjà la forme JVM pour un tableau (« [Ljava.lang.String; »), aux
            // points près.
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
                // Inatteignable : la liste ci-dessus est celle des primitifs de Java.
                default -> throw new IllegalStateException(type.getName());
            };
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private record Membres(ImmutableList<String> champs, ImmutableList<String> methodes) {}

    private static Membres lire(Class<?> clazz) {
        // Le .class est demandé au chargeur de la classe elle-même : c'est lui qui l'a trouvée,
        // il sait donc le retrouver — y compris quand la classe vient du répertoire analysé,
        // hors de notre propre classpath.
        String ressource = "/" + clazz.getName().replace('.', '/') + ".class";

        try (InputStream octets = clazz.getResourceAsStream(ressource)) {
            if (octets == null) {
                return null;
            }
            return lire(new DataInputStream(octets));
        } catch (IOException | RuntimeException e) {
            // Un fichier tronqué ou d'un format inattendu : l'ordre de déclaration est perdu,
            // pas la génération. L'appelant se rabat sur l'alphabet et le signale.
            return null;
        }
    }

    private static Membres lire(DataInputStream entree) throws IOException {
        entree.readInt(); // la signature 0xCAFEBABE
        entree.readInt(); // les versions mineure et majeure

        // Le réservoir de constantes : seules les chaînes UTF-8 sont conservées, ce sont elles
        // que les tables de membres désignent par leur indice.
        int taille = entree.readUnsignedShort();
        String[] chaines = new String[taille];
        for (int i = 1; i < taille; i++) {
            int nature = entree.readUnsignedByte();
            switch (nature) {
                // CONSTANT_Utf8 : readUTF lit la longueur puis les octets, exactement le format.
                case 1 -> chaines[i] = entree.readUTF();
                // Class, String, MethodType, Module, Package : un indice de deux octets.
                case 7, 8, 16, 19, 20 -> sauter(entree, 2);
                // MethodHandle : une sorte et un indice.
                case 15 -> sauter(entree, 3);
                // Integer, Float, les références, NameAndType, Dynamic, InvokeDynamic.
                case 3, 4, 9, 10, 11, 12, 17, 18 -> sauter(entree, 4);
                // Long et Double occupent deux entrées du réservoir : la seconde n'existe pas.
                case 5, 6 -> {
                    sauter(entree, 8);
                    i++;
                }
                default -> throw new IOException("unknown constant pool tag " + nature);
            }
        }

        sauter(entree, 2); // les drapeaux d'accès
        sauter(entree, 4); // la classe elle-même et sa superclasse
        sauter(entree, 2 * entree.readUnsignedShort()); // les interfaces

        ImmutableList.Builder<String> champs = ImmutableList.builder();
        int nombreDeChamps = entree.readUnsignedShort();
        for (int i = 0; i < nombreDeChamps; i++) {
            sauter(entree, 2); // les drapeaux d'accès
            champs.add(chaines[entree.readUnsignedShort()]);
            sauter(entree, 2); // le descripteur, inutile : deux champs ne partagent pas un nom
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
            sauter(entree, 2); // le nom de l'attribut
            sauter(entree, entree.readInt());
        }
    }

    // skipBytes peut sauter moins que demandé sans que rien ne soit fini : on insiste, et une
    // fin de fichier prématurée est une erreur de lecture comme une autre.
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

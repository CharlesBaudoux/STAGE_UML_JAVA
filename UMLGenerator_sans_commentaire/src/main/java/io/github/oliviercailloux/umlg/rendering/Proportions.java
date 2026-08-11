package io.github.oliviercailloux.umlg.rendering;

import io.github.oliviercailloux.umlg.Avertissements;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Proportions {

    private static final double RAPPORT_MAXIMAL = 4.0;

    private static final int TAILLE_SIGNIFICATIVE = 1500;

    private static final Pattern LARGEUR_SVG = Pattern.compile("width=\"(\\d+)px\"");

    private static final Pattern HAUTEUR_SVG = Pattern.compile("height=\"(\\d+)px\"");

    private Proportions() {}

    static boolean verifier(Path fichier, String vue) throws IOException {
        int[] dimensions = dimensions(fichier);
        if (dimensions == null) {
            return false;
        }
        int largeur = dimensions[0];
        int hauteur = dimensions[1];
        if (largeur == 0 || hauteur == 0) {
            return false;
        }
        double rapport = Math.max((double) largeur / hauteur, (double) hauteur / largeur);

        if (rapport <= RAPPORT_MAXIMAL
            || Math.max(largeur, hauteur) < TAILLE_SIGNIFICATIVE) {
            return false;
        }
        Avertissements.dire("view \"" + vue + "\" is " + largeur + "×" + hauteur
            + " pixels, a ratio of " + Math.round(rapport) + ":1. Such a diagram is hard to read"
            + " once printed. Try another reading direction for this view, split it into smaller"
            + " ones, or show fewer members.");
        return true;
    }

    private static int[] dimensions(Path fichier) throws IOException {
        String nom = fichier.getFileName().toString().toLowerCase(Locale.ROOT);

        if (nom.endsWith(".svg")) {
            String debut = entete(fichier, 2048);
            Matcher largeur = LARGEUR_SVG.matcher(debut);
            Matcher hauteur = HAUTEUR_SVG.matcher(debut);
            return largeur.find() && hauteur.find()
                ? new int[] {Integer.parseInt(largeur.group(1)),
                    Integer.parseInt(hauteur.group(1))}
                : null;
        }
        if (nom.endsWith(".png")) {
            byte[] octets = Files.readAllBytes(fichier);
            if (octets.length < 24) {
                return null;
            }
            return new int[] {entier(octets, 16), entier(octets, 20)};
        }
        return null;
    }

    private static String entete(Path fichier, int octets) throws IOException {
        byte[] tout = Files.readAllBytes(fichier);
        return new String(tout, 0, Math.min(octets, tout.length), StandardCharsets.UTF_8);
    }

    private static int entier(byte[] octets, int debut) {
        return ((octets[debut] & 0xFF) << 24) | ((octets[debut + 1] & 0xFF) << 16)
            | ((octets[debut + 2] & 0xFF) << 8) | (octets[debut + 3] & 0xFF);
    }
}

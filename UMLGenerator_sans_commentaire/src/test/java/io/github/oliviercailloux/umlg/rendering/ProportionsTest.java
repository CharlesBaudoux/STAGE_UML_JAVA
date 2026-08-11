package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProportionsTest {

    private static Path svg(Path dossier, String nom, int largeur, int hauteur)
        throws IOException {
        Path fichier = dossier.resolve(nom + ".svg");
        Files.writeString(fichier,
            "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" height=\""
                + hauteur + "px\" preserveAspectRatio=\"none\" style=\"width:" + largeur
                + "px;height:" + hauteur + "px;\" viewBox=\"0 0 " + largeur + " " + hauteur
                + "\" width=\"" + largeur + "px\"></svg>",
            StandardCharsets.UTF_8);
        return fichier;
    }

    @Test
    void unRubanTresLarge_estSignale(@TempDir Path tmp) throws IOException {
        assertTrue(Proportions.verifier(svg(tmp, "ruban", 3570, 289), "Representable"));
    }

    @Test
    void unDessinTresHaut_estSignaleAussi(@TempDir Path tmp) throws IOException {
        assertTrue(Proportions.verifier(svg(tmp, "colonne", 300, 3000), "Colonne"));
    }

    @Test
    void unDessinEquilibre_neDitRien(@TempDir Path tmp) throws IOException {
        assertFalse(Proportions.verifier(svg(tmp, "mapping", 1157, 488), "Mapping"));
        assertFalse(Proportions.verifier(svg(tmp, "mnemonics", 1069, 497), "Mnemonics"));
    }

    @Test
    void unePetiteImage_nEstPasSignaleeMemeAllongee(@TempDir Path tmp) throws IOException {
        assertFalse(Proportions.verifier(svg(tmp, "petite", 800, 100), "Petite"));
    }

    @Test
    void justeSousLeSeuil_neDitRien(@TempDir Path tmp) throws IOException {
        assertFalse(Proportions.verifier(svg(tmp, "limite", 2000, 520), "Limite"));
    }

    @Test
    void unPng_estMesureAussi(@TempDir Path tmp) throws IOException {
        Path fichier = tmp.resolve("ruban.png");
        byte[] png = new byte[24];
        ecrire(png, 16, 3570);
        ecrire(png, 20, 289);
        Files.write(fichier, png);

        assertTrue(Proportions.verifier(fichier, "Ruban"));
    }

    @Test
    void unFormatInconnu_neDeclencheRien(@TempDir Path tmp) throws IOException {
        Path fichier = tmp.resolve("notes.txt");
        Files.writeString(fichier, "ceci n'est pas une image", StandardCharsets.UTF_8);

        assertFalse(Proportions.verifier(fichier, "Notes"));
    }

    private static void ecrire(byte[] octets, int debut, int valeur) {
        octets[debut] = (byte) (valeur >>> 24);
        octets[debut + 1] = (byte) (valeur >>> 16);
        octets[debut + 2] = (byte) (valeur >>> 8);
        octets[debut + 3] = (byte) valeur;
    }
}

package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Le contrôle des proportions : quelles images sont signalées, et surtout lesquelles ne le sont
// pas.
//
// La seconde moitié compte autant que la première. Un avertissement qui se déclenche sur un
// dessin lisible apprend à ne plus les lire — et celui-ci s'adresse à un utilisateur qui n'a rien
// fait de faux, puisque son diagramme est exact.
class ProportionsTest {

    // Un SVG réduit à son en-tête : le contrôle n'a besoin que des dimensions, et les fabriquer
    // évite de faire tourner le moteur de rendu pour éprouver une règle de trois.
    //
    // L'ordre des attributs imite celui du moteur — hauteur avant largeur, par ordre alphabétique
    // — et ce détail n'en est pas un : un faux fichier qui les écrirait dans l'ordre attendu par
    // le code validerait une lecture qui échoue sur tout fichier réel. C'est arrivé.
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
        // Le cas mesuré qui a motivé ce contrôle : 3570 sur 289, soit 12 pour 1.
        assertTrue(Proportions.verifier(svg(tmp, "ruban", 3570, 289), "Representable"));
    }

    @Test
    void unDessinTresHaut_estSignaleAussi(@TempDir Path tmp) throws IOException {
        // Le déséquilibre se juge dans les deux sens : une colonne de trois mille pixels de haut
        // n'est pas plus lisible qu'un ruban de trois mille de large.
        assertTrue(Proportions.verifier(svg(tmp, "colonne", 300, 3000), "Colonne"));
    }

    @Test
    void unDessinEquilibre_neDitRien(@TempDir Path tmp) throws IOException {
        // Les proportions mesurées sur les vues bien composées d'un projet réel.
        assertFalse(Proportions.verifier(svg(tmp, "mapping", 1157, 488), "Mapping"));
        assertFalse(Proportions.verifier(svg(tmp, "mnemonics", 1069, 497), "Mnemonics"));
    }

    @Test
    void unePetiteImage_nEstPasSignaleeMemeAllongee(@TempDir Path tmp) throws IOException {
        // Une image de quelques centaines de pixels reste lisible même très allongée : la
        // signaler serait un faux reproche, et c'est ce que le seuil de taille écarte.
        assertFalse(Proportions.verifier(svg(tmp, "petite", 800, 100), "Petite"));
    }

    @Test
    void justeSousLeSeuil_neDitRien(@TempDir Path tmp) throws IOException {
        // 3,9 pour 1 sur une grande image : déséquilibré, mais pas au point qu'on s'en mêle.
        assertFalse(Proportions.verifier(svg(tmp, "limite", 2000, 520), "Limite"));
    }

    @Test
    void unPng_estMesureAussi(@TempDir Path tmp) throws IOException {
        // Les dimensions d'un PNG se lisent dans son bloc IHDR, et non dans un en-tête textuel :
        // le contrôle doit connaître les deux formats qu'écrit la bibliothèque.
        Path fichier = tmp.resolve("ruban.png");
        byte[] png = new byte[24];
        // Signature, longueur et type du bloc importent peu ici : seuls comptent les deux entiers
        // gros-boutistes qui suivent, aux octets 16 et 20.
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

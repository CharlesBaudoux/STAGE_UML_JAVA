package io.github.oliviercailloux.umlg.rendering;

import io.github.oliviercailloux.umlg.warning.Avertisseur;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Mesure l'image produite, et signale celle qu'on ne pourra pas lire.
//
// POURQUOI CE CONTRÔLE EXISTE
//
// Un diagramme peut être juste et illisible. Une vue dont les boîtes s'alignent sur une seule
// rangée donne un ruban de plusieurs milliers de pixels de large sur quelques centaines de haut :
// imprimé sur une page, chaque boîte y fait deux millimètres. Rien n'a échoué, la source est
// correcte, et l'utilisateur ne le découvre qu'en ouvrant le fichier.
//
// La bibliothèque, elle, le sait au moment d'écrire : il suffit de relire les dimensions de ce
// qu'elle vient de produire. Le dire coûte une ligne sur la sortie d'erreur ; le taire laisse
// chercher pourquoi le dessin est inutilisable.
//
// CE QU'ON NE PEUT PAS FAIRE À LA PLACE DE L'UTILISATEUR
//
// Corriger. Les causes sont plusieurs — un sens de lecture qui ne convient pas à cette vue-ci,
// des boîtes trop larges, une vue qui retient trop de classes — et le choix entre elles lui
// appartient. L'avertissement nomme donc la vue et rappelle les remèdes, sans rien décider.
final class Proportions {

    // Au-delà de ce rapport entre la plus grande et la plus petite dimension, le dessin cesse
    // d'être lisible. Les vues bien composées mesurées sur des projets réels se tiennent entre
    // 1,5 et 3 ; celle qui a motivé ce contrôle atteignait 12.
    private static final double RAPPORT_MAXIMAL = 4.0;

    // En deçà de cette taille, un rapport élevé ne gêne personne : une petite image reste lisible
    // même allongée, et la signaler serait un faux reproche.
    private static final int TAILLE_SIGNIFICATIVE = 1500;

    // « width="1234px" » et « height="567px" » dans l'en-tête du SVG.
    //
    // Deux motifs indépendants, et non un seul qui les attendrait l'un après l'autre : le moteur
    // écrit ses attributs par ordre alphabétique, donc la hauteur avant la largeur. Un motif qui
    // fige leur ordre marche sur un fichier fabriqué à la main et sur aucun fichier réel.
    private static final Pattern LARGEUR_SVG = Pattern.compile("width=\"(\\d+)px\"");

    private static final Pattern HAUTEUR_SVG = Pattern.compile("height=\"(\\d+)px\"");

    private Proportions() {}

    // Signale l'image si ses proportions la rendent illisible. Rend vrai si un avertissement a
    // été émis, ce dont les tests se servent.
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
        Avertisseur.dire("view \"" + vue + "\" is " + largeur + "×" + hauteur
            + " pixels, a ratio of " + Math.round(rapport) + ":1. Such a diagram is hard to read"
            + " once printed. Try another reading direction for this view, split it into smaller"
            + " ones, or show fewer members.");
        return true;
    }

    // Les dimensions de l'image, ou null si le format n'est pas reconnu.
    private static int[] dimensions(Path fichier) throws IOException {
        String nom = fichier.getFileName().toString().toLowerCase(Locale.ROOT);

        if (nom.endsWith(".svg")) {
            // L'en-tête suffit : les dimensions y figurent dans les premiers octets, et relire
            // un SVG entier pour deux nombres serait du gaspillage sur un gros diagramme.
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
            // Le bloc IHDR suit les huit octets de signature et les huit de longueur et de type :
            // la largeur occupe les octets 16 à 19, la hauteur les suivants, en gros-boutiste.
            if (octets.length < 24) {
                return null;
            }
            return new int[] {entier(octets, 16), entier(octets, 20)};
        }
        return null;
    }

    // Les premiers octets du fichier, et eux seuls.
    //
    // La méthode lisait le fichier entier avant de le tronquer, ce que son appelant croyait
    // précisément éviter : sur un diagramme de plusieurs mégaoctets, tout était chargé en mémoire
    // pour y chercher deux nombres écrits dans les deux cents premiers octets. readNBytes lit ce
    // qu'on lui demande, et s'arrête là.
    //
    // Une coupe au milieu d'un caractère UTF-8 ne produit qu'un caractère de remplacement, sans
    // conséquence : les attributs recherchés sont en ASCII, et se trouvent bien avant la fin.
    private static String entete(Path fichier, int octets) throws IOException {
        byte[] debut = new byte[octets];
        int lus;
        try (InputStream flux = Files.newInputStream(fichier)) {
            lus = flux.readNBytes(debut, 0, octets);
        }
        return new String(debut, 0, lus, StandardCharsets.UTF_8);
    }

    private static int entier(byte[] octets, int debut) {
        return ((octets[debut] & 0xFF) << 24) | ((octets[debut + 1] & 0xFF) << 16)
            | ((octets[debut + 2] & 0xFF) << 8) | (octets[debut + 3] & 0xFF);
    }
}

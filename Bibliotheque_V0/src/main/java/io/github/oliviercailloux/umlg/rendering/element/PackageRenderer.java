package io.github.oliviercailloux.umlg.rendering.element;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import java.util.LinkedHashMap;
import java.util.Map;

// Range les boîtes dans leurs rectangles, et écrit l'ouverture de chacun.
//
// Forme produite :
//   package "Module Paiement" #EEEEEE {
//     class "Facture" as Facture {
//     }
//   }
//
// Deux responsabilités, tenues ensemble parce qu'elles répondent à la même question — « quel
// rectangle, et de quelle couleur ? » : constituer les groupes à partir des entités, et écrire
// la ligne qui ouvre l'un d'eux.
public final class PackageRenderer {

    // La ligne qui referme un rectangle. Une constante plutôt qu'un littéral répété : l'ouverture
    // et la fermeture doivent rester appariées, et se lire l'une près de l'autre.
    public static final String FERMETURE = "}\n\n";

    private PackageRenderer() {}

    // Les entités rangées par groupe, dans l'ordre où les groupes apparaissent.
    //
    // La clé est le groupe retenu — nom et couleur —, et non le nom seul : l'appelant n'a plus
    // ensuite qu'à écrire ce qu'on lui donne. Les entités sans groupe sont rassemblées sous
    // UmlPackage.aucun(), première clé de la table quand il y en a : elles se dessinent au
    // premier niveau, et les voir en tête de la source engendrée est plus lisible.
    //
    // LA COULEUR QUAND ELLE EST DÉCLARÉE DEUX FOIS
    //
    // Un rectangle n'a qu'une couleur, et deux classes du même groupe peuvent en déclarer deux.
    // La première rencontrée l'emporte — il faut bien trancher —, et l'écart est signalé plutôt
    // que tu : l'auteur a écrit une couleur qu'il ne verra pas, et rien sur le dessin ne le lui
    // dirait.
    public static ImmutableList<Map.Entry<UmlPackage, ImmutableList<UmlEntity>>> groupes(
        Iterable<UmlEntity> entites) {

        // Clé : le nom du groupe, qui en est l'identité. Une LinkedHashMap pour que l'ordre des
        // rectangles dans le fichier soit celui de leur première apparition, et non celui d'un
        // hachage — la source engendrée doit être reproductible.
        Map<String, UmlPackage> retenus = new LinkedHashMap<>();
        Map<String, ImmutableList.Builder<UmlEntity>> membres = new LinkedHashMap<>();
        // Le nom complet de la classe qui a fixé la couleur d'un groupe, pour le message.
        Map<String, String> premiereCouleur = new LinkedHashMap<>();

        for (UmlEntity entite : entites) {
            UmlPackage groupe = entite.group();
            String cle = groupe.name();

            UmlPackage retenu = retenus.get(cle);
            if (retenu == null) {
                retenus.put(cle, groupe);
                membres.put(cle, ImmutableList.builder());
                if (groupe.estGroupee() && !groupe.color().isEmpty()) {
                    premiereCouleur.put(cle, entite.qualifiedName());
                }
            } else if (groupe.estGroupee() && !groupe.color().isEmpty()
                && !groupe.color().equals(retenu.color())) {

                if (retenu.color().isEmpty()) {
                    // Le groupe n'avait pas encore de couleur : celle-ci n'en concurrence
                    // aucune, et rien ne justifierait de la perdre — ni de le signaler.
                    retenus.put(cle, groupe);
                    premiereCouleur.put(cle, entite.qualifiedName());
                } else {
                    // Deux couleurs déclarées pour un rectangle qui n'en a qu'une : celle-ci est
                    // réellement perdue, et c'est le seul cas où il y a quelque chose à dire.
                    signalerCouleurEnDouble(entite, groupe, retenu, premiereCouleur.get(cle));
                }
            }
            membres.get(cle).add(entite);
        }

        ImmutableList.Builder<Map.Entry<UmlPackage, ImmutableList<UmlEntity>>> resultat =
            ImmutableList.builder();
        for (Map.Entry<String, UmlPackage> groupe : retenus.entrySet()) {
            resultat.add(Map.entry(groupe.getValue(), membres.get(groupe.getKey()).build()));
        }
        return resultat.build();
    }

    // La ligne qui ouvre un rectangle. Le nom est entre guillemets, ce qui permet espaces et
    // accents ; la couleur, elle, est validée par ColorCodec plutôt qu'échappée — son
    // vocabulaire est fermé.
    public static String ouverture(UmlPackage groupe) {
        StringBuilder ligne = new StringBuilder("package \"")
            .append(PlantUmlEscaper.label(groupe.name())).append('"');

        if (!groupe.color().isEmpty()) {
            ligne.append(' ').append(ColorCodec.code(groupe.color(), groupe.name()));
        }
        return ligne.append(" {\n").toString();
    }

    // L'écart est dit sur la sortie d'erreur, faute d'un meilleur canal à ce stade : la
    // génération ne peut pas s'arrêter — le diagramme reste juste, il est seulement d'une autre
    // couleur que celle qu'on croyait — et le processeur d'annotations, qui aurait dit la même
    // chose plus tôt, ne voit pas toujours les deux classes dans la même compilation.
    private static void signalerCouleurEnDouble(UmlEntity entite, UmlPackage declare,
        UmlPackage retenu, String premiere) {
        System.err.println("Warning: @UMLPackage on " + entite.qualifiedName() + ": group \""
            + declare.name() + "\" already has the color \"" + retenu.color() + "\""
            + (premiere == null ? "" : ", set by " + premiere)
            + "; a rectangle has only one, so \"" + declare.color() + "\" will not be drawn.");
    }
}

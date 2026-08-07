package io.github.oliviercailloux.umlg.extraction.rules;

// Les familles de membres qu'un projet peut déclarer sans intérêt pour son diagramme.
//
// Aucune n'est masquée tant que le fichier de règles ne le demande pas. C'est un choix, et il
// n'est pas neutre : la bibliothèque écartait autrefois d'office les accesseurs et les méthodes
// d'Object, au motif qu'ils n'apprennent rien. Le motif est bon dans la plupart des projets et
// mauvais dans certains — sur un code immuable à la Guava, il n'y a pas un seul getter à écarter,
// et ce qui encombre est ailleurs. Décider à la place de l'utilisateur revenait à lui retirer du
// diagramme des membres qu'il n'avait jamais demandé à cacher, sans que rien ne le signale.
//
// Chaque famille se déclare par son nom, en toutes lettres : « hide accessors ».
public enum Bruit {

    // getX(), isX(), setX(v), au sens strict de la convention JavaBean : le nom, le nombre de
    // paramètres et le type de retour doivent tous concorder. Un getPrix(Client) reste une
    // méthode métier.
    ACCESSORS("accessors"),

    // toString(), hashCode(), equals(Object) — les trois signatures exactes d'Object, que tout
    // le monde redéfinit et que personne ne lit sur un diagramme.
    OBJECT_METHODS("object-methods"),

    // Les accesseurs qu'un record engendre pour ses composantes : nom() pour la composante nom.
    // Ils ne suivent pas la convention get/is, d'où une famille à part.
    RECORD_ACCESSORS("record-accessors"),

    // values() et valueOf(String), que le compilateur ajoute à toute énumération.
    ENUM_METHODS("enum-methods");

    private final String motCle;

    Bruit(String motCle) {
        this.motCle = motCle;
    }

    // Le mot qu'on écrit dans le fichier, après « hide ».
    public String motCle() {
        return motCle;
    }

    // La famille portant ce mot-clé, ou null s'il n'en désigne aucune. Null plutôt qu'une
    // exception : c'est le lecteur du fichier qui compose le message d'erreur, lui seul
    // connaissant le numéro de ligne.
    static Bruit parMotCle(String motCle) {
        for (Bruit famille : values()) {
            if (famille.motCle.equals(motCle)) {
                return famille;
            }
        }
        return null;
    }

    // Les mots-clés connus, pour le message qui en rappelle la liste.
    static String motsCles() {
        StringBuilder connus = new StringBuilder();
        for (Bruit famille : values()) {
            connus.append(connus.isEmpty() ? "" : ", ").append(famille.motCle);
        }
        return connus.toString();
    }
}

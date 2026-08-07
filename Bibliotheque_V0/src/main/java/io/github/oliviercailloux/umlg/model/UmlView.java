package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;

// Un diagramme parmi ceux que le projet produit : un sous-ensemble de boîtes, dans un ordre, avec
// son titre et son sens de lecture.
//
// POURQUOI PLUSIEURS DIAGRAMMES
//
// Un projet de trente classes donne un dessin que personne ne lit. Ce qu'on veut lire, ce sont
// quelques diagrammes de cinq à dix boîtes, chacun répondant à une question — « comment se fait
// le mapping ? », « qu'est-ce qui gravite autour de cette classe ? ». La vue intégrale garde sa
// valeur d'inventaire, pas d'explication.
//
// CE QUE LES ENTITÉS D'UNE VUE ONT DE PARTICULIER
//
// Ce sont celles du projet, à deux restrictions près, toutes deux appliquées à la construction :
// leurs relations vers une boîte absente de la vue ont été retirées — un trait ne peut pas
// rejoindre ce qui n'est pas dessiné —, et l'ordre est celui que la vue demande.
//
// Elles ne sont donc pas interchangeables avec celles du projet, et c'est voulu : le rendu écrit
// ce qu'on lui donne, sans avoir à savoir dans quelle vue il se trouve.
public record UmlView(String name, ImmutableList<UmlEntity> entities, UMLFlow direction) {

    public UmlView {
        checkNotNull(name, "The view name cannot be null.");
        checkNotNull(entities, "The list of entities cannot be null.");
        checkNotNull(direction, "The direction cannot be null; use UMLFlow.TOP_TO_BOTTOM.");

        checkArgument(!name.isBlank(), "The view name cannot be empty or made of whitespace only.");
        // Une vue vide ne se distinguerait pas d'un fichier oublié. Le cas est refusé ici plutôt
        // qu'à l'écriture, où l'on ne saurait plus dire d'où la vue venait.
        checkArgument(!entities.isEmpty(), "View \"%s\" is empty; it would draw nothing.", name);
    }

    // Le nom du fichier que cette vue produit, sans extension.
    //
    // Le nom de la vue est écrit par un humain et peut contenir espaces, accents et ponctuation ;
    // un nom de fichier, lui, doit traverser trois systèmes de fichiers sans surprise. Tout ce
    // qui n'est ni lettre, ni chiffre, ni tiret devient un tiret, et les tirets ne s'accumulent
    // pas.
    public String nomDeFichier() {
        // Les accents sont d'abord détachés de leur lettre (NFD : « é » devient « e » suivi d'un
        // accent aigu), puis les accents seuls sont retirés. Un nom de fichier accentué survit
        // bien à un système moderne, moins bien à une archive, à un partage réseau ou à une URL.
        String sansAccents = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        String remplace = sansAccents.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-");
        String propre = remplace.replaceAll("^-+|-+$", "");
        // Un nom fait uniquement de ponctuation ne laisserait rien : on retombe alors sur un mot
        // neutre plutôt que sur un fichier sans nom.
        return propre.isEmpty() ? "vue" : propre.toLowerCase(java.util.Locale.ROOT);
    }
}

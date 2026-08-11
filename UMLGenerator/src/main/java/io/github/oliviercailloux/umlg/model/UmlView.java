package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import java.util.Locale;

/**
 * Un diagramme parmi ceux que le projet produit : un sous-ensemble de boîtes, dans un ordre, avec
 * son nom et son sens de lecture.
 *
 * <p><b>Pourquoi plusieurs diagrammes.</b> Un projet de trente classes donne un dessin que
 * personne ne lit. Ce qu'on veut lire, ce sont quelques diagrammes de cinq à dix boîtes, chacun
 * répondant à une question — « comment se fait le mapping ? », « qu'est-ce qui gravite autour de
 * cette classe ? ». La vue intégrale garde sa valeur d'inventaire, pas d'explication.
 *
 * <p><b>Ce que les entités d'une vue ont de particulier.</b> Ce sont celles du projet, à deux
 * restrictions près, toutes deux appliquées à la construction : leurs relations vers une boîte
 * absente de la vue ont été retirées — un trait ne peut pas rejoindre ce qui n'est pas dessiné —,
 * et l'ordre est celui que la vue demande. Elles ne sont donc pas interchangeables avec celles du
 * projet, et c'est voulu : le rendu écrit ce qu'on lui donne, sans avoir à savoir dans quelle vue
 * il se trouve.
 *
 * @param name le nom écrit par l'auteur, dont se déduit le nom du fichier produit. Ni nul ni
 *     vide.
 * @param entities les boîtes retenues, dans l'ordre demandé. Jamais vide.
 * @param direction le sens de lecture propre à cette vue.
 */
public record UmlView(String name, ImmutableList<UmlEntity> entities, UMLFlow direction) {

    /**
     * Vérifie les invariants de la vue.
     *
     * @throws NullPointerException si l'une des composantes est nulle.
     * @throws IllegalArgumentException si le nom est vide ou fait d'espaces, ou si la vue ne
     *     retient aucune entité : elle ne se distinguerait pas d'un fichier oublié.
     */
    public UmlView {
        checkNotNull(name, "The view name cannot be null.");
        checkNotNull(entities, "The list of entities cannot be null.");
        checkNotNull(direction, "The direction cannot be null; use UMLFlow.TOP_TO_BOTTOM.");

        checkArgument(!name.isBlank(), "The view name cannot be empty or made of whitespace only.");
        // Une vue vide ne se distinguerait pas d'un fichier oublié. Le cas est refusé ici plutôt
        // qu'à l'écriture, où l'on ne saurait plus dire d'où la vue venait.
        checkArgument(!entities.isEmpty(), "View \"%s\" is empty; it would draw nothing.", name);
    }

    /**
     * Le nom du fichier que cette vue produit, sans extension.
     *
     * <p>Le nom de la vue est écrit par un humain et peut contenir espaces, accents et
     * ponctuation ; un nom de fichier, lui, doit traverser trois systèmes de fichiers sans
     * surprise. Tout ce qui n'est ni lettre ni chiffre devient un tiret, les tirets ne
     * s'accumulent pas, et les accents sont retirés.
     *
     * @return un nom en minuscules, jamais vide : un nom fait uniquement de ponctuation retombe
     *     sur {@code vue}.
     */
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
        return propre.isEmpty() ? "vue" : propre.toLowerCase(Locale.ROOT);
    }
}

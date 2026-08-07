package com.banque.contrat;

import io.github.oliviercailloux.umlg.annotation.*;

/**
 * Couvre : la nature INTERFACE d'une boîte, et la <em>réalisation</em> — le trait pointillé à
 * triangle creux qu'engendrent les {@code implements} de {@code Client} et de {@code Compte}.
 *
 * <p>Aucune annotation ne déclare ces liens : {@code InheritanceExtractor} relit la signature
 * Java elle-même. Seul le périmètre reste déclaré — une interface hors du diagramme, comme
 * {@code Comparable}, est simplement ignorée.
 */
@UMLClass(stereotype = "Contract", strategy = UMLVisibility.PUBLIC)
@UMLNote(text = "Réalisée par ClientPremium et par Compte :\ndeux traits pointillés à triangle creux.")
public interface Identifiable {

    String identifiant();
}

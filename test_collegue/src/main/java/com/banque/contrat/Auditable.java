package com.banque.contrat;

import io.github.oliviercailloux.umlg.annotation.*;

/**
 * Couvre : la <em>généralisation</em> entre deux interfaces — trait plein, et non pointillé.
 *
 * <p>Java écrit {@code extends} ici et {@code implements} là ; la réflexion range les deux dans
 * {@code getInterfaces()}. C'est la nature de la source qui les départage, et une interface qui
 * en étend une autre en hérite bel et bien : généralisation.
 */
@UMLClass(stereotype = "Contract", strategy = UMLVisibility.PUBLIC)
public interface Auditable extends Identifiable {

    String empreinteAudit();
}

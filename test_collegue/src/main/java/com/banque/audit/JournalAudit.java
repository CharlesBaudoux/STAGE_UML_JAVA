package com.banque.audit;

import com.banque.modele.Compte;
import com.banque.modele.Operation;
import io.github.oliviercailloux.umlg.annotation.*;
import java.util.List;

/**
 * Couvre : une association portée par un joker borné, ses rôle et cardinalité côté source, une
 * contrainte posée sur ce lien, et une @UMLDependency unique.
 *
 * <p>{@code List<? extends Operation>} ne nomme pas directement sa cible ; target la nomme.
 */
@UMLClass(stereotype = "Service", strategy = UMLVisibility.PUBLIC)
@UMLDependency(target = Compte.class, label = "audite")
public class JournalAudit {

    @UMLAssociation(
        target = Operation.class,
        sourceCardinality = "1",
        sourceRole = "journal",
        targetCardinality = "0..*",
        targetRole = "consignées")
    @UMLConstraint(rule = "ordered")
    @UMLConstraint(rule = "lecture seule")
    private List<? extends Operation> operationsSuspectes;

    public void consigner(Operation operation) {}
}

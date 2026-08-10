package com.banque.api;

import com.banque.audit.JournalAudit;
import com.banque.modele.Client;
import com.banque.modele.Devise;
import io.github.oliviercailloux.umlg.annotation.*;
import java.util.Map;

/**
 * Couvre : color avec '#', @UMLInclude et @UMLExclude, l'association qualifiée vers une classe
 * d'un autre paquetage, @UMLNote orientée vers le haut, et @UMLDependency répétée — avec et
 * sans label.
 *
 * <p>Les dépendances sont ici la seule façon de révéler ces liens : ni le service de
 * notification ni le journal d'audit ne sont des attributs, ils ne sont qu'utilisés au passage
 * dans le corps des méthodes. Aucun attribut ne les porte.
 */
@UMLClass(strategy = UMLVisibility.PUBLIC, stereotype = "Facade", color = "#E2E2E2")
@UMLDependency(target = ServiceNotification.class, label = "notifie via")
@UMLDependency(target = JournalAudit.class, label = "trace dans")
@UMLDependency(target = Devise.class)
@UMLNote(text = "Seul point d'entrée du domaine.", direction = UMLDirection.UP)
public class BanqueFacade {

    // private : hors stratégie PUBLIC, donc invisible.
    private String apiKey;

    // Dérogation positive : forcé sur le diagramme malgré sa visibilité.
    @UMLInclude
    private String secretInterne;

    /** Cible portée par une Map : le target dit laquelle des deux moitiés est visée. */
    @UMLAssociation(
        target = Client.class,
        targetCardinality = "0..*",
        targetRole = "clients",
        label = "expose")
    private Map<String, Client> annuaire;

    public void transfererFonds() {
        ServiceNotification service = new ServiceNotification();
        service.envoyer("transfert effectué");
    }

    /** Dérogation négative : cachée bien que publique. */
    @UMLExclude
    public void verifierStatutSysteme() {}
}

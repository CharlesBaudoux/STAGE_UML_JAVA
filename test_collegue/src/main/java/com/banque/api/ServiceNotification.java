package com.banque.api;

import io.github.oliviercailloux.umlg.annotation.*;

/** Cible d'une @UMLDependency : elle doit elle-même porter @UMLClass pour être visée. */
@UMLClass(stereotype = "Service", strategy = UMLVisibility.PUBLIC)
public class ServiceNotification {

    public void envoyer(String message) {}
}

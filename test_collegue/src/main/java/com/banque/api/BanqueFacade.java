package com.banque.api;
import io.github.oliviercailloux.umlg.annotation.*;

@UMLClass(strategy = UMLVisibility.PUBLIC, stereotype = "Facade", color = "#E2E2E2")
public class BanqueFacade {
    private String apiKey; // Normalement invisible (car private)

    @UMLInclude
    private String secretInterne; // Sera forcé sur le diagramme (Exception positive)

    public void transfererFonds() {} // Visible (car public)

    @UMLExclude
    public void verifierStatutSysteme() {} // Sera caché malgré le fait qu'elle soit public (Exception négative)
}

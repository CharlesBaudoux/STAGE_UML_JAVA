package com.banque.modele;
import io.github.oliviercailloux.umlg.annotation.*;

@UMLClass(name = "ClientPremium", stereotype = "Entity", color = "ADD8E6", strategy = UMLVisibility.PRIVATE)
public class Client {
    private String nom;
    private String email;
    public int age;
}

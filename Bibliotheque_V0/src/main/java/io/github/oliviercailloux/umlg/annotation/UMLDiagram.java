package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Les réglages du diagramme dans son ensemble, déclarés une fois par projet.
//
// Toutes les autres annotations décrivent le contenu du modèle : cette classe-ci est une boîte,
// cet attribut-là est un lien. Celle-ci décrit sa présentation d'ensemble — son titre, son sens
// de lecture — c'est-à-dire ce qui n'appartient à aucun élément en particulier.
//
// PACKAGE : elle se pose sur un package-info.java, seul endroit où Java permet d'annoter un
// paquetage. Un seul du projet peut la porter ; deux paquetages qui la déclareraient tous les
// deux donneraient deux diagrammes concurrents, et rien ne dirait lequel fait foi (voir
// ProjectScanner).
//
//   @UMLDiagram(title = "Architecture du module Facturation",
//               direction = UMLFlow.LEFT_TO_RIGHT)
//   package com.exemple.facturation;
//
// RUNTIME : comme les autres, elle est lue par réflexion sur le package-info.class. Ce fichier
// n'est engendré par javac que si le package-info.java porte au moins une annotation — ce qui
// est exactement le cas qui nous intéresse.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface UMLDiagram {

    // Titre affiché en tête du diagramme. Vide = aucun titre, et pas même la place qu'il
    // occuperait.
    String title() default "";

    // Le sens dans lequel le dessin se déplie. Voir UMLFlow.
    UMLFlow direction() default UMLFlow.TOP_TO_BOTTOM;

    // Faut-il masquer les compartiments d'attributs et de méthodes restés vides ?
    //
    // Vrai par défaut, et ce défaut n'est pas neutre : le défaut de @UMLClass est
    // strategy = NONE, qui produit précisément des boîtes sans membre. Sans ce masquage, le cas
    // nominal de la bibliothèque — une classe annotée, rien de plus — dessinerait des rectangles
    // vides sous chaque nom.
    //
    // Le paramètre est donc une dérogation, et se lit dans ce sens : hideEmptyMembers = false
    // veut dire « montre-les tout de même », ce qu'on demande pour un diagramme où l'on veut
    // voir que les compartiments existent.
    boolean hideEmptyMembers() default true;
}

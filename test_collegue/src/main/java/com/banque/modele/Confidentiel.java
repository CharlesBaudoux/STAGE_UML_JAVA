package com.banque.modele;

import io.github.oliviercailloux.umlg.annotation.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Couvre : la nature ANNOTATION d'une boîte.
 *
 * <p>Pour la réflexion un type annotation est une interface ; {@code EntityExtractor} le teste
 * en premier, précisément pour ne pas les confondre. PlantUML sait dessiner les deux à part, et
 * la boîte s'ouvre donc sur le mot-clé {@code annotation}.
 */
@UMLClass(stereotype = "Marker")
@UMLNote(text = "Boîte de nature « annotation » :\nun @interface, pas une interface.")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Confidentiel {
}

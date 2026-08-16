package io.github.oliviercailloux.keyboardd.representable;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

import io.github.oliviercailloux.geometry.Point;
import io.github.oliviercailloux.geometry.Size;
import io.github.oliviercailloux.geometry.Zone;

/**
 * A key as physical object in a keyboard, with (optionnally) the signal that it sends.
 * <p>
 * The unit is 1 cm.
 */
// Sans « strategy », pour la meme raison que CanonicalMnemonic : les composants se dessinent
// seuls, et PRIVATE n'ajoutait plus que les accesseurs et les methodes d'Object.
@UMLClass
public record RectangularKey (Zone zone,
    /* empty for no signal */ String xKeyName) {
  public static RectangularKey from(Zone zone, String xKeyName) {
    return new RectangularKey(zone, xKeyName);
    // return new RectangularKey(Zone.at(topLeftCorner).resizedFixedCenter(size), xKeyName);
  }
}

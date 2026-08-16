package io.github.oliviercailloux.keyboardd.mapping;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.Objects;
import java.util.Optional;

/**
 * A keysym mnemonic (in the form of a `String`), a Unicode Code Point (an integer), or a keysym
 * code (an integer), representing an entry in an XKB symbols file.
 */
@UMLClass
public sealed interface KeysymEntry
    permits KeysymEntry.Mnemonic, KeysymEntry.Ucp, KeysymEntry.Code {

  // Onze lignes d'annotations en moins sur ces trois records : leurs composants sont leur
  // definition, umlg les dessine desormais quelle que soit la strategie — plus besoin de
  // « strategy = PRIVATE » — et asString(), exclue une fois sur la declaration ci-dessous,
  // n'a plus a l'etre sur chaque override.
  // Les composants se nomment « mnemonic » et « code », comme le diagramme de reference les
  // ecrit : dans KeysymEntry.Mnemonic, le prefixe « keysym » est deja porte par l'interface.
  //
  // Le renommage est fait dans le code plutot que par @UMLInclude(name) : sur un composant de
  // record, l'annotation se propage a la fois au champ et a l'accesseur, et l'accesseur reparait
  // alors dans la boite — une inclusion explicite l'emporte sur « hide record-accessors ».
  @UMLClass
  public static record Mnemonic (String mnemonic) implements KeysymEntry {
    @Override
    public String asString() {
      return mnemonic();
    }
  }

  @UMLClass
  public static record Ucp (int ucp) implements KeysymEntry {
    @Override
    public String asString() {
      return new String(Character.toChars(ucp));
    }
  }

  @UMLClass
  public static record Code (int code) implements KeysymEntry {
    @Override
    public String asString() {
      return String.valueOf(code());
    }
  }

  String asString();
}

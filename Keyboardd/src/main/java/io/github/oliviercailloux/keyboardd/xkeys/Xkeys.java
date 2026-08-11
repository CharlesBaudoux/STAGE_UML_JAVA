package io.github.oliviercailloux.keyboardd.xkeys;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import java.io.IOException;
import java.util.Map;

/**
 * The set of X key names (canonical and aliases) and corresponding X keycodes available on a
 * system.
 */
@UMLClass
public interface Xkeys {
  @UMLInclude
  public static Xkeys latest() {
    return EvdevReader.latest();
  }

  /**
   *
   * @param xKeycodes
   * @param canonicalXKeyNameByAlias keys are all aliases, values are only canonicals
   * @return
   */
  public static Xkeys fromMaps(Map<String, Short> xKeycodes,
      Map<String, String> canonicalXKeyNameByAlias) {
    return XkeysImpl.fromMaps(xKeycodes, canonicalXKeyNameByAlias);
  }

  // Le parametre s'appelle « evdev » et non « source » : le diagramme de reference le nomme
  // ainsi, et c'est le nom le plus juste — la source attendue est un fichier evdev.
  @UMLInclude
  public static Xkeys fromSource(CharSource evdev) throws IOException {
    return EvdevReader.parse(evdev);
  }

  ImmutableSet<Short> codes();

  /**
   *
   * @return disjoint from aliases
   */
  ImmutableSet<String> canonicals();

  String canonical(short code);

  /**
   *
   * @param keyName a legal X key name
   * @return equal to argument iff given is canonical
   */
  String canonical(String keyName);

  /**
   *
   * @return key set equals the legal names; values equals the legal codes;
   */
  // Les trois accesseurs-attributs sont declares dans l'ordre ou le diagramme de reference les
  // ecrit : « order declaration » lit l'ordre du code, c'est donc ici qu'il se decide.
  @UMLInclude(name = "xKeycodeByXKeyName", asAttribute = true)
  ImmutableMap<String, Short> codeByName();

  /**
   *
   * @return key set equals the canonicals; values equals the legal codes
   */
  // Le README ecrit les noms non abreges : le diagramme retrouve le sien sans renommer le code.
  // Et il ecrit ces accesseurs en notation attribut — « xKeycodeByCanonicalXKeyName :
  // BiMap<String, Short> », sans parentheses — parce qu'ils exposent une propriete de l'objet ;
  // les fabriques latest() et fromSource(), elles, gardent leurs parentheses.
  @UMLInclude(name = "xKeycodeByCanonicalXKeyName", asAttribute = true)
  ImmutableBiMap<String, Short> codeByCanonical();

  /**
   * @return key set equals the legal codes; values equals the canonicals
   */
  ImmutableBiMap<Short, String> canonicalByCode();

  /**
   *
   * @return disjoint from canonicals
   */
  ImmutableSet<String> aliases();

  /**
   *
   * @param canonicalXKeyName
   * @return may be empty, not containing the canonical
   */
  ImmutableSet<String> aliases(String canonicalXKeyName);

  /**
   *
   * @return key set equals the aliases; values include only canonicals
   */
  @UMLInclude(name = "canonicalXKeyNameByAlias", asAttribute = true)
  ImmutableMap<String, String> canonicalByAlias();

  /**
   *
   * @return union of canonicals and aliases
   */
  ImmutableSet<String> names();

  /**
   *
   * @param code a legal code
   * @return not empty, canonical is first element
   */
  ImmutableSet<String> names(short code);

  /**
   *
   * @param keyName a legal X key name
   * @return true iff is not canonical
   */
  boolean isAlias(String keyName);

  /**
   *
   * @param keyName a legal X key name
   * @return
   */
  short code(String keyName);

  @UMLInclude
  Xkeys withoutAliases();

  @Override
  boolean equals(Object o2);
}

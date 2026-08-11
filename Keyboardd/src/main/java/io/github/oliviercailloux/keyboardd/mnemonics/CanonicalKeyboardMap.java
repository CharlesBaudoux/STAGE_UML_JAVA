package io.github.oliviercailloux.keyboardd.mnemonics;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.keyboardd.mapping.KeyboardMap;
import java.util.Map;

@UMLClass
public class CanonicalKeyboardMap {
  /**
   * Creates a canonical keyboard map from the given keyboard map and mnemonics.
   *
   * @param map must have been canonicalized with {@link KeyboardMap#canonicalize}
   * @param mnemonics the mnemonics to be used in the canonical keyboard map
   * @return a canonical keyboard map
   */
  @UMLInclude
  public static CanonicalKeyboardMap canonicalize(KeyboardMap map, Mnemonics mnemonics) {
    return new CanonicalKeyboardMap(map, mnemonics);
  }

  private final ImmutableListMultimap<String, CanonicalKeysymEntry> xKeyNameToEntries;

  private CanonicalKeyboardMap(KeyboardMap map, Mnemonics mnemonics) {
    xKeyNameToEntries = map.nameToEntries().entries().stream()
        .collect(ImmutableListMultimap.toImmutableListMultimap(Map.Entry::getKey,
            entry -> mnemonics.canonicalize(entry.getValue())));
  }

  /**
   * The X key names found in this keyboard map.
   *
   * @return empty iff this keyboard map is empty; a set of canonical X key names
   */
  public ImmutableSet<String> names() {
    return xKeyNameToEntries.keySet();
  }

  /**
   * The keysym entries associated to the given X key name.
   *
   * @param xKeyName the canonical X key name
   * @return an empty list iff the given X key name is not found in this keyboard map
   */
  @UMLInclude
  public ImmutableList<CanonicalKeysymEntry> entries(String xKeyName) {
    return xKeyNameToEntries.get(xKeyName);
  }

  /**
   * The association of canonical X key names to keysym entries found in this keyboard map.
   *
   * @return a map that is empty iff this keyboard map is empty; containing no empty lists
   */
  public ImmutableListMultimap<String, CanonicalKeysymEntry> nameToEntries() {
    return xKeyNameToEntries;
  }
}

package io.github.oliviercailloux.keyboardd.representable;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.keyboardd.mnemonics.CanonicalKeyboardMap;
import io.github.oliviercailloux.keyboardd.mnemonics.CanonicalKeysymEntry;
import java.util.function.Function;

@UMLClass
public class CanonicalKeyboardMapRepresenter implements XKeyNamesAndRepresenter {
  @UMLInclude
  public static CanonicalKeyboardMapRepresenter from(CanonicalKeyboardMap keyboardMap,
      Function<CanonicalKeysymEntry, Representation> representations) {
    return new CanonicalKeyboardMapRepresenter(keyboardMap, representations);
  }

  private final CanonicalKeyboardMap keyboardMap;
  private final Function<CanonicalKeysymEntry, Representation> representations;
  private final XKeyNamesAndRepresenter representer;

  public CanonicalKeyboardMapRepresenter(CanonicalKeyboardMap keyboardMap,
      Function<CanonicalKeysymEntry, Representation> representations) {
    this.keyboardMap = keyboardMap;
    this.representations = representations;
    representer =
        XKeyNamesAndRepresenter.fromIndirect(keyboardMap.nameToEntries(), representations);
  }

  @Override
  public ImmutableSet<String> names() {
    return representer.names();
  }

  public CanonicalKeyboardMap keyboardMap() {
    return keyboardMap;
  }

  @UMLInclude
  public ImmutableList<CanonicalKeysymEntry> entries(String name) {
    return keyboardMap.entries(name);
  }

  public Representation representation(CanonicalKeysymEntry entry) {
    return representations.apply(entry);
  }

  @Override
  public ImmutableListMultimap<String, Representation> representations() {
    return representer.representations();
  }

  @Override
  public ImmutableList<Representation> representations(String name) {
    return representer.representations(name);
  }
}

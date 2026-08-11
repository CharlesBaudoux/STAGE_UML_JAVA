package io.github.oliviercailloux.keyboardd.mapping;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * An association of X key names to a list of keysym entries, representing a set of directives found
 * typically in XKB symbol files.
 * <p>
 * Two such keyboard maps are considered equal iff they have the same X key names, and for each X
 * key name, the corresponding lists of keysym entries are equal.
 */
@UMLClass
public class KeyboardMap {
  /**
   * Creates a keyboard map from the given association of lists of keysym entries to each X key
   * name.
   *
   * @param xKeyNameToKeysymEntries the X key names and corresponding keysym entries to be used in
   *        the keyboard map; may be empty; may not contain empty lists
   * @return a keyboard map
   */
  public static KeyboardMap from(ListMultimap<String, KeysymEntry> xKeyNameToKeysymEntries) {
    return new KeyboardMap(xKeyNameToKeysymEntries);
  }

  private final ImmutableListMultimap<String, KeysymEntry> xKeyNameToEntries;
  private ImmutableSetMultimap<String, String> mnemonicToXKeyNames;
  private ImmutableSetMultimap<Integer, String> ucpToXKeyNames;
  private ImmutableSetMultimap<Integer, String> codeToXKeyNames;

  private KeyboardMap(ListMultimap<String, KeysymEntry> xKeyNameToKeysymEntries) {
    checkArgument(
        !xKeyNameToKeysymEntries.asMap().values().contains(ImmutableList.<KeysymEntry>of()));
    this.xKeyNameToEntries = ImmutableListMultimap.copyOf(xKeyNameToKeysymEntries);
    mnemonicToXKeyNames = null;
    ucpToXKeyNames = null;
    codeToXKeyNames = null;
  }

  /**
   * The X key names found in this keyboard map.
   *
   * @return empty iff this keyboard map is empty
   */
  public ImmutableSet<String> names() {
    return xKeyNameToEntries.keySet();
  }

  /**
   * The keysym entries associated to the given X key name.
   *
   * @param xKeyName the X key name
   * @return an empty list iff the given X key name is not found in this keyboard map
   */
  @UMLInclude
  public ImmutableList<KeysymEntry> entries(String xKeyName) {
    return xKeyNameToEntries.get(xKeyName);
  }

  /**
   * The association of X key names to keysym entries found in this keyboard map.
   *
   * @return a map that is empty iff this keyboard map is empty; containing no empty lists
   */
  public ImmutableListMultimap<String, KeysymEntry> nameToEntries() {
    return xKeyNameToEntries;
  }

  private void lazyInitReverse() {
    verify((mnemonicToXKeyNames == null) == (ucpToXKeyNames == null));
    verify((mnemonicToXKeyNames == null) == (codeToXKeyNames == null));

    if (mnemonicToXKeyNames == null) {
      ImmutableListMultimap<KeysymEntry, String> fromAllEntries = xKeyNameToEntries.inverse();
      ImmutableMap<KeysymEntry, Collection<String>> fromAllEntriesMap = fromAllEntries.asMap();
      {
        Map<KeysymEntry, Collection<String>> fromMnemonicEntriesMap =
            Maps.filterKeys(fromAllEntriesMap, e -> e instanceof KeysymEntry.Mnemonic);
        ImmutableMap<String, Collection<String>> fromMnemonicMap = CollectionUtils.transformKeys(
            fromMnemonicEntriesMap, e -> ((KeysymEntry.Mnemonic) e).mnemonic());
        mnemonicToXKeyNames = fromMnemonicMap.entrySet().stream().collect(ImmutableSetMultimap
            .flatteningToImmutableSetMultimap(e -> e.getKey(), e -> e.getValue().stream()));
      }
      {
        Map<KeysymEntry, Collection<String>> fromUcpEntriesMap =
            Maps.filterKeys(fromAllEntriesMap, e -> e instanceof KeysymEntry.Ucp);
        ImmutableMap<Integer, Collection<String>> fromUcpMap =
            CollectionUtils.transformKeys(fromUcpEntriesMap, e -> ((KeysymEntry.Ucp) e).ucp());
        ucpToXKeyNames = fromUcpMap.entrySet().stream().collect(ImmutableSetMultimap
            .flatteningToImmutableSetMultimap(e -> e.getKey(), e -> e.getValue().stream()));
      }
      {
        Map<KeysymEntry, Collection<String>> fromCodeEntriesMap =
            Maps.filterKeys(fromAllEntriesMap, e -> e instanceof KeysymEntry.Code);
        ImmutableMap<Integer, Collection<String>> fromCodeMap = CollectionUtils
            .transformKeys(fromCodeEntriesMap, e -> ((KeysymEntry.Code) e).code());
        codeToXKeyNames = fromCodeMap.entrySet().stream().collect(ImmutableSetMultimap
            .flatteningToImmutableSetMultimap(e -> e.getKey(), e -> e.getValue().stream()));
      }
    }
  }

  /**
   * The X key names that are associated to the given keysym mnemonic.
   *
   * @param keysymMnemonic a keysym mnemonic
   * @return empty iff the given keysym mnemonic is not found in this keyboard map
   */
  public ImmutableSet<String> namesFromMnemonic(String keysymMnemonic) {
    lazyInitReverse();
    return mnemonicToXKeyNames.get(keysymMnemonic);
  }

  /**
   * The X key names that are associated to the given Unicode code point.
   *
   * @param ucp a Unicode code point
   * @return empty iff the given Unicode code point is not found in this keyboard map
   */
  public ImmutableSet<String> namesFromUcp(int ucp) {
    lazyInitReverse();
    return ucpToXKeyNames.get(ucp);
  }

  /**
   * The X key names that are associated to the given keysym code.
   *
   * @param keysymCode a keysym code
   * @return empty iff the given keysym code is not found in this keyboard map
   */
  public ImmutableSet<String> namesFromCode(int keysymCode) {
    lazyInitReverse();
    return codeToXKeyNames.get(keysymCode);
  }

  /**
   * Returns a keyboard map that replaces the aliases by the corresponding canonical name, as given
   * in argument.
   *
   * @param canonicalXKeyNameByAlias a map from aliases to canonical names
   * @return a keyboard map using only the canonical X key names.
   */
  @UMLInclude
  public KeyboardMap canonicalize(Map<String, String> canonicalXKeyNameByAlias) {
    final ImmutableMap.Builder<String, String> newNameFromOriginalBuilder =
        new ImmutableMap.Builder<>();

    ImmutableSet<String> originalNames = xKeyNameToEntries.keySet();
    for (String originalName : originalNames) {
      String newName = canonicalXKeyNameByAlias.getOrDefault(originalName, originalName);
      newNameFromOriginalBuilder.put(originalName, newName);
    }
    ImmutableMap<String, String> newNameFromOriginal = newNameFromOriginalBuilder.build();
    ImmutableSetMultimap<String, String> originalsFromNewName =
        newNameFromOriginal.asMultimap().inverse();
    ImmutableSet<String> duplicatedNewNames =
        originalsFromNewName.keySet().stream().filter(n -> originalsFromNewName.get(n).size() >= 2)
            .collect(ImmutableSet.toImmutableSet());
    // originalsFromNewName.keySet().stream().filter(n -> originalsFromNewName.get(n).size() >= 2)
    // .collect(ImmutableSetMultimap.toImmutableSetMultimap(n -> originalsFromNewName));
    // checkState(duplicatedNewNames.isEmpty(), "These ");
    if (!duplicatedNewNames.isEmpty()) {
      final ImmutableSetMultimap.Builder<String, String> duplicatedMapBuilder =
          new ImmutableSetMultimap.Builder<>();
      for (String duplicatedNewName : duplicatedNewNames) {
        ImmutableSet<String> originals = originalsFromNewName.get(duplicatedNewName);
        duplicatedMapBuilder.putAll(duplicatedNewName, originals);
      }
      ImmutableSetMultimap<String, String> duplicatedMap = duplicatedMapBuilder.build();
      throw new IllegalStateException(
          "These new names are pointed to from several original names: %s."
              .formatted(duplicatedMap));
    }
    // ImmutableBiMap<String, String> originalFromNewName =
    ImmutableBiMap.copyOf(newNameFromOriginal).inverse();

    ImmutableListMultimap.Builder<String, KeysymEntry> withNewNames =
        ImmutableListMultimap.builder();
    for (String original : originalNames) {
      withNewNames.putAll(newNameFromOriginal.get(original), xKeyNameToEntries.get(original));
    }
    ImmutableListMultimap<String, KeysymEntry> withNewNamesBuilt = withNewNames.build();
    return KeyboardMap.from(withNewNamesBuilt);
  }

  public KeyboardMap overwrite(KeyboardMap other) {
    ImmutableListMultimap.Builder<String, KeysymEntry> builder = ImmutableListMultimap.builder();

    ImmutableSet<String> onlyInThis = Sets.difference(names(), other.names()).immutableCopy();
    builder.putAll(
        xKeyNameToEntries.entries().stream().filter(e -> onlyInThis.contains(e.getKey())).collect(
            ImmutableListMultimap.toImmutableListMultimap(e -> e.getKey(), e -> e.getValue())));

    builder.putAll(other.xKeyNameToEntries);

    return KeyboardMap.from(builder.build());
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof KeyboardMap)) {
      return false;
    }
    final KeyboardMap t2 = (KeyboardMap) o2;
    return xKeyNameToEntries.equals(t2.xKeyNameToEntries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(xKeyNameToEntries);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("xKeyNameToEntries", xKeyNameToEntries).toString();
  }
}

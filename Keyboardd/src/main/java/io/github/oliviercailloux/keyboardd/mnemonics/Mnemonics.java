package io.github.oliviercailloux.keyboardd.mnemonics;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.io.CharSource;
import io.github.oliviercailloux.keyboardd.mapping.KeysymEntry;
import io.github.oliviercailloux.keyboardd.mnemonics.KeysymReader.ParsedMnemonic;
import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * Association of the mnemonics to their code and aliases and possibly UCP and whether they are
 * deprecated.
 * <ul>
 * <li>Maps each keysym mnemonic to exactly one keysym code.</li>
 * <li>Indicates, for each keysym mnemonic, whether it is deprecated.</li>
 * <li>Maps each keysym mnemonic to its canonical and aliases relatives (meaning the keysym
 * mnemonics that are associated to the same code).</li>
 * <li>Maps each keysym code to at most one keysym mnemonic. If that keysym mnemonic is deprecated,
 * then all its aliases are as well.</li>
 * <li>Maps each keysym mnemonic to at most one UCP.</li>
 * <li>Maps each UCP to at most one canonical keysym mnemonic.</li>
 * </ul>
 * <p>
 */
@UMLClass
public class Mnemonics {
  @UMLInclude
  public static Mnemonics latest() {
    ImmutableSet<ParsedMnemonic> parsedMns = KeysymReader.latest();
    return toMnemonics(parsedMns);
  }

  // Declaree ici, entre latest() et fromSource(), pour que « order declaration » place les trois
  // fabriques dans l'ordre du diagramme de reference.
  @UMLInclude
  public static Mnemonics fromCanonicals(Set<CanonicalMnemonic> canonicalMnemonics) {
    return new Mnemonics(canonicalMnemonics);
  }

  /**
   * If the given source maps several non deprecated and non specific codes (through different
   * mnemonics) to a given UCP, it must have exactly one of these codes in the range
   * UcpByCode#IMPLICIT_UCP_KEYSYM_CODES. Only the mnemonics associated to that code will be
   * considered associated to that UCP, not the mnemonics associated to other codes.
   */
  @UMLInclude
  public static Mnemonics fromSource(CharSource keysyms) throws IOException {
    ImmutableSet<ParsedMnemonic> parsedMns = KeysymReader.parse(keysyms);
    return toMnemonics(parsedMns);
  }

  private static Mnemonics toMnemonics(ImmutableSet<ParsedMnemonic> parsedMns) {
    /*
     * We want to define equivalence classes: p equiv p' iff they have the same code (implying the
     * same ucp or all absent ucp) OR the same ucp (NOT implying the same code). Each such class is
     * then mapped to one canonical mnemonic per code in the class. This mapping depends on the
     * whole equivalence class (it is not separable).
     */
    final ImmutableSet.Builder<ImmutableSet<ParsedMnemonic>> equivalenceClassesBuilder =
        new ImmutableSet.Builder<>();
    for (ParsedMnemonic parsedMn : parsedMns) {
      equivalenceClassesBuilder.add(parsedMns.stream()
          .filter(p -> parsedMn.code() == p.code()
              || (parsedMn.unicode().isPresent() && parsedMn.unicode().equals(p.unicode())))
          .collect(ImmutableSet.toImmutableSet()));
    }
    ImmutableSet<ImmutableSet<ParsedMnemonic>> equivalenceClasses =
        equivalenceClassesBuilder.build();
    ImmutableSet<CanonicalMnemonic> canonicals = equivalenceClasses.stream()
        .flatMap(s -> toCanonicalsSoleUcp(s).stream()).collect(ImmutableSet.toImmutableSet());

    return new Mnemonics(canonicals);
  }

  private static ImmutableSet<CanonicalMnemonic>
      toCanonicalsSoleUcp(Set<ParsedMnemonic> parsedMns) {
    Optional<Integer> ucp = soleUcp(parsedMns);
    ImmutableMap<ParsedMnemonic, Integer> mnToCode =
        parsedMns.stream().collect(ImmutableMap.toImmutableMap(m -> m, m -> m.code()));
    ImmutableSetMultimap<Integer, ParsedMnemonic> codeToMns = mnToCode.asMultimap().inverse();
    verify(codeToMns.keySet().size() >= 1);
    if (codeToMns.keySet().size() >= 2) {
      verify(ucp.isPresent(), parsedMns.toString());
    }
    int codeThatKeepsUcp = getCodeThatKeepsUcp(parsedMns);
    final ImmutableSet.Builder<CanonicalMnemonic> mnsBuilder = new ImmutableSet.Builder<>();
    for (int code : codeToMns.keySet()) {
      mnsBuilder.add(toCanonical(codeToMns.get(code), code == codeThatKeepsUcp));
    }
    return mnsBuilder.build();
  }

  private static Optional<Integer> soleUcp(Set<ParsedMnemonic> parsedMns) {
    ImmutableSet<Integer> ucps =
        parsedMns.stream().map(ParsedMnemonic::unicode).filter(Optional::isPresent)
            .flatMap(Optional::stream).distinct().collect(ImmutableSet.toImmutableSet());
    verify(ucps.size() <= 1, parsedMns.toString());
    Optional<Integer> ucp = ucps.stream().collect(MoreCollectors.toOptional());
    return ucp;
  }

  private static int getCodeThatKeepsUcp(Set<ParsedMnemonic> parsedMns) {
    Comparator<ParsedMnemonic> mainComparator = Comparator
        .<ParsedMnemonic, Boolean>comparing(p -> !p.deprecated()).thenComparing(p -> !p.specific())
        .thenComparing(p -> ImplicitUcp.IMPLICIT_UCP_KEYSYM_CODES.contains(p.code()));
    Comparator<ParsedMnemonic> comparatorCompatibleWithEquals =
        mainComparator.thenComparing(ParsedMnemonic::mnemonic);
    ImmutableSortedSet<ParsedMnemonic> sortedMns =
        ImmutableSortedSet.copyOf(comparatorCompatibleWithEquals, parsedMns);
    ParsedMnemonic best = sortedMns.last();
    int bestCode = best.code();
    Optional<ParsedMnemonic> secondBestOpt =
        sortedMns.stream().filter(p -> p.code() != bestCode).findFirst();
    if (secondBestOpt.isPresent()) {
      ParsedMnemonic secondBest = secondBestOpt.orElseThrow();
      checkArgument(mainComparator.compare(best, secondBest) != 0,
          "Cannot decide which code to keep among %s.", sortedMns);
    }
    return bestCode;
  }

  /**
   * A remaining (thus non-first mnemonic for a given code) is generally deprecated, unless
   * explicitly marked so, so we have to consider a wider notion of deprecation than what is visible
   * from line-by-line myopic parsing.
   * 
   * @param remaining non-first mnemonic for a given code
   * @return true, unless has been recognized as a non deprecated alias when parsing
   * @see https://github.com/xkbcommon/libxkbcommon/issues/433
   */
  private static boolean deprecated(ParsedMnemonic remaining) {
    boolean notDeprecated = remaining.alias() && !remaining.deprecated();
    return !notDeprecated;
  }

  private static CanonicalMnemonic toCanonical(Set<ParsedMnemonic> parsedMns, boolean keepUcp) {
    Iterator<ParsedMnemonic> iterator = parsedMns.iterator();
    checkArgument(iterator.hasNext());
    ParsedMnemonic first = iterator.next();
    ImmutableSet<ParsedMnemonic> remaining =
        ImmutableSet.<ParsedMnemonic>builder().addAll(iterator).build();
    boolean deprecated = first.deprecated();
    if (deprecated) {
      checkArgument(parsedMns.stream().allMatch(Mnemonics::deprecated));
    }
    int code = parsedMns.stream().map(ParsedMnemonic::code).distinct()
        .collect(MoreCollectors.onlyElement());
    Optional<Integer> ucp;
    if (keepUcp) {
      ucp = soleUcp(parsedMns);
    } else {
      ucp = Optional.empty();
    }
    ImmutableSet<ParsedMnemonic> deprecateds =
        remaining.stream().filter(Mnemonics::deprecated).collect(ImmutableSet.toImmutableSet());
    ImmutableSet<ParsedMnemonic> nonDeprecateds =
        remaining.stream().filter(p -> !deprecated(p)).collect(ImmutableSet.toImmutableSet());
    return new CanonicalMnemonic(first.mnemonic(), code,
        nonDeprecateds.stream().map(ParsedMnemonic::mnemonic)
            .collect(ImmutableSet.toImmutableSet()),
        deprecateds.stream().map(ParsedMnemonic::mnemonic).collect(ImmutableSet.toImmutableSet()),
        ucp, deprecated);
  }

  // Le diagramme de reference nomme les deux premieres tables par ce qu'elles indexent — le
  // mnemonique et le code d'un keysym —, la ou le code s'appuie sur le contexte de la classe.
  // Le renommage vit sur l'annotation parce qu'il porte sur ces champs-ci, et non sur une regle.
  @UMLInclude(name = "byKeysymMnemonic")
  private final ImmutableMap<String, CanonicalMnemonic> byMnemonic;
  @UMLInclude(name = "byKeysymCode")
  private final ImmutableBiMap<Integer, CanonicalMnemonic> byCode;
  @UMLInclude
  private final ImmutableBiMap<Integer, CanonicalMnemonic> byUcp;

  private Mnemonics(Set<CanonicalMnemonic> canonicalMnemonics) {
    final ImmutableMap.Builder<String, CanonicalMnemonic> byMnemonicBuilder =
        new ImmutableMap.Builder<>();
    for (CanonicalMnemonic canonicalMnemonic : canonicalMnemonics) {
      byMnemonicBuilder.putAll(Maps.asMap(canonicalMnemonic.mnemonics(), m -> canonicalMnemonic));
    }
    byMnemonic = byMnemonicBuilder.build();

    byCode = canonicalMnemonics.stream()
        .collect(ImmutableBiMap.toImmutableBiMap(CanonicalMnemonic::code, c -> c));

    byUcp = canonicalMnemonics.stream().filter(c -> c.ucp().isPresent())
        .collect(ImmutableBiMap.toImmutableBiMap(c -> c.ucp().orElseThrow(), c -> c));
  }

  public CanonicalMnemonic canonical(String keysymMnemonic) {
    checkArgument(byMnemonic.containsKey(keysymMnemonic), "Unknown mnemonic: %s.", keysymMnemonic);
    return byMnemonic.get(keysymMnemonic);
  }

  /**
   *
   * @return keyset: all keysym mnemonics; values: all canonical keysym mnemonics, including any
   *         deprecated ones
   */
  public ImmutableMap<String, CanonicalMnemonic> byMnemonic() {
    return byMnemonic;
  }

  /**
   *
   * @return keyset: all keysym codes; values: all canonical keysym mnemonics, including any
   *         deprecated ones
   */
  public ImmutableBiMap<Integer, CanonicalMnemonic> byCode() {
    return byCode;
  }

  /**
   *
   * @return keyset: the UCP bound to a mnemonic; values: the mnemonics bound to a UCP, some of
   *         which might be deprecated
   */
  public ImmutableBiMap<Integer, CanonicalMnemonic> byUcp() {
    return byUcp;
  }

  /**
   * This loses entries that are mapped only to deprecated mnemonics.
   */
  @UMLInclude
  public Mnemonics withoutDeprecated() {
    Set<CanonicalMnemonic> nonDCan = byMnemonic.values().stream().filter(c -> !c.deprecated())
        .collect(ImmutableSet.toImmutableSet());
    ImmutableSet<CanonicalMnemonic> nonD = nonDCan.stream().map(Mnemonics::withoutDeprecatedAliases)
        .collect(ImmutableSet.toImmutableSet());
    return new Mnemonics(nonD);
  }

  public CanonicalKeysymEntry canonicalize(KeysymEntry keysymEntry) {
    final CanonicalKeysymEntry canonical;
    if (keysymEntry instanceof KeysymEntry.Mnemonic mnemonic) {
      canonical = canonical(mnemonic.mnemonic());
    } else if (keysymEntry instanceof KeysymEntry.Ucp ucp) {
      if (byUcp.containsKey(ucp.ucp())) {
        canonical = byUcp.get(ucp.ucp());
      } else {
        canonical = ImplicitUcp.byUcp(ucp.ucp());
      }
    } else {
      verify(keysymEntry instanceof KeysymEntry.Code);
      KeysymEntry.Code code = (KeysymEntry.Code) keysymEntry;
      if (byCode.containsKey(((KeysymEntry.Code) keysymEntry).code())) {
        canonical = byCode.get(code.code());
      } else {
        canonical = ImplicitUcp.byCode(((KeysymEntry.Code) keysymEntry).code());
      }
    }
    return canonical;
  }

  private static CanonicalMnemonic withoutDeprecatedAliases(CanonicalMnemonic canonicalMnemonic) {
    return new CanonicalMnemonic(canonicalMnemonic.mnemonic(), canonicalMnemonic.code(),
        canonicalMnemonic.nonDeprecatedAliases(), ImmutableSet.of(), canonicalMnemonic.ucp(),
        canonicalMnemonic.deprecated());
  }
}

package io.github.oliviercailloux.keyboardd.mnemonics;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;

// Sans « strategy » : les composants d'un record sont sa definition, umlg les dessine quelle que
// soit la strategie. Demander PRIVATE ne les ajoutait plus, mais faisait entrer avec eux les
// accesseurs et les methodes d'Object.
@UMLClass
public record CanonicalMnemonic (String mnemonic, int code,
    ImmutableSet<String> nonDeprecatedAliases,
    ImmutableSet<String> deprecatedAliases,
    Optional<Integer> ucp,
    boolean deprecated) implements CanonicalKeysymEntry {
  public CanonicalMnemonic {
    checkArgument(!mnemonic.isEmpty());
    checkArgument(Sets.intersection(nonDeprecatedAliases, deprecatedAliases).isEmpty());
    if (deprecated) {
      checkArgument(nonDeprecatedAliases.isEmpty());
    }
  }

  /**
   * Retrieves the mnemonics associated to the same code as this one.
   *
   * @return the relative mnemonics, starting with this one, followed by the non deprecated
   *         aliases and finally by the deprecated aliases.
   */
  public ImmutableSet<String> mnemonics() {
    return ImmutableSet.<String>builder().add(mnemonic).addAll(nonDeprecatedAliases)
        .addAll(deprecatedAliases).build();
  }

  /**
   * Retrieves the mnemonics associated to the same code as this one, except for this one.
   *
   * @return the non deprecated aliases followed by the deprecated aliases.
   */
  public ImmutableSet<String> aliases() {
    return ImmutableSet.<String>builder().addAll(nonDeprecatedAliases).addAll(deprecatedAliases)
        .build();
  }
}
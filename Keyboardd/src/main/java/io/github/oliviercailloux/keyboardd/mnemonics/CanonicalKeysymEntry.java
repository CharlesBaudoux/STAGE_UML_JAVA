package io.github.oliviercailloux.keyboardd.mnemonics;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

import java.util.Optional;

@UMLClass
public sealed interface CanonicalKeysymEntry permits CanonicalMnemonic, ImplicitUcp {
  default Optional<Integer> probeUcp() {
    if (this instanceof ImplicitUcp ucp) {
      return Optional.of(ucp.ucp());
    }
    CanonicalMnemonic mnemonic = (CanonicalMnemonic) this;
    return mnemonic.ucp();
  }
}

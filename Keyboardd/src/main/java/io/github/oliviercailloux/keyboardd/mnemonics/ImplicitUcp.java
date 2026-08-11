package io.github.oliviercailloux.keyboardd.mnemonics;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

import com.google.common.collect.ContiguousSet;
import io.github.oliviercailloux.keyboardd.mapping.KeysymEntry;
import java.util.function.Function;

@UMLClass
public record ImplicitUcp (int ucp) implements CanonicalKeysymEntry {
  public static final ContiguousSet<Integer> IMPLICIT_UCPS = ContiguousSet.closed(0x100, 0x10F_FFF);
  public static final ContiguousSet<Integer> IMPLICIT_UCP_KEYSYM_CODES =
      ContiguousSet.closed(0x01_000_100, 0x01_10F_FFF);

  public static final Function<Integer, Integer> IMPLICIT_UCP_BY_CODE = c -> c - 0x01_000_000;
  public static final Function<Integer, Integer> CODE_BY_IMPLICIT_UCP = u -> u + 0x01_000_000;

  public static ImplicitUcp byUcp(int ucp) {
    return new ImplicitUcp(ucp);
  }

  public static ImplicitUcp byCode(int code) {
    return new ImplicitUcp(IMPLICIT_UCP_BY_CODE.apply(code));
  }

  public int code() {
    return CODE_BY_IMPLICIT_UCP.apply(ucp);
  }

  public String asString() {
    return new KeysymEntry.Ucp(ucp).asString();
  }
}

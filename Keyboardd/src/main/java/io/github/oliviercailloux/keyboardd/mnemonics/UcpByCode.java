package io.github.oliviercailloux.keyboardd.mnemonics;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Might consider removing this: only IMPLICIT_UCP_BY_CODE and CODE_BY_IMPLICIT_UCP are used. */
class UcpByCode {
  public static UcpByCode implicit() {
    return new UcpByCode(ImmutableMap.of(), ImplicitUcp.IMPLICIT_UCP_KEYSYM_CODES, ImplicitUcp.IMPLICIT_UCPS);
  }

  public static UcpByCode implicitAndExplicit(Map<Integer, Integer> ucpByCodeExplicit) {
    ContiguousSet<Integer> domain = ImplicitUcp.IMPLICIT_UCP_KEYSYM_CODES;
    ImmutableSet.Builder<Integer> domainBuilder = ImmutableSet.builder();

    ContiguousSet<Integer> coDomain = ImplicitUcp.IMPLICIT_UCPS;
    ImmutableSet.Builder<Integer> coDomainBuilder = ImmutableSet.builder();

    return new UcpByCode(ucpByCodeExplicit,
        domainBuilder.addAll(domain).addAll(ucpByCodeExplicit.keySet()).build(),
        coDomainBuilder.addAll(coDomain).addAll(ucpByCodeExplicit.values()).build());
  }

  public static UcpByCode implicitAndExplicit(Mnemonics mnemonics) {
    ImmutableBiMap<Integer, CanonicalMnemonic> byUcp = mnemonics.byUcp();
    ImmutableBiMap<Integer, Integer> codeByUcp = byUcp.keySet().stream()
        .collect(ImmutableBiMap.toImmutableBiMap(u -> u, u -> byUcp.get(u).code()));
    return implicitAndExplicit(codeByUcp.inverse());
  }

  private final ImmutableMap<Integer, Integer> ucpByCodeExplicit;
  private final ImmutableSet<Integer> domainOfCodes;
  private final ImmutableSet<Integer> coDomainOfUcps;

  private UcpByCode(Map<Integer, Integer> ucpByCodeExplicit, Set<Integer> domain,
      Set<Integer> coDomain) {
    checkArgument(domain.size() >= coDomain.size());

    this.ucpByCodeExplicit = ImmutableMap.copyOf(ucpByCodeExplicit);
    checkArgument(domain.containsAll(ucpByCodeExplicit.keySet()));
    this.domainOfCodes = ImmutableSet.copyOf(domain);
    checkArgument(coDomain.containsAll(ucpByCodeExplicit.values()));
    this.coDomainOfUcps = ImmutableSet.copyOf(coDomain);
  }

  public ImmutableSet<Integer> domainOfCodes() {
    return domainOfCodes;
  }

  public ImmutableSet<Integer> coDomainOfUcps() {
    return coDomainOfUcps;
  }

  public int ucp(int code) {
    checkArgument(domainOfCodes.contains(code));
    if (ucpByCodeExplicit.containsKey(code)) {
      return ucpByCodeExplicit.get(code);
    }
    int ucp = ImplicitUcp.IMPLICIT_UCP_BY_CODE.apply(code);
    verify(coDomainOfUcps.contains(ucp));
    return ucp;
  }
}

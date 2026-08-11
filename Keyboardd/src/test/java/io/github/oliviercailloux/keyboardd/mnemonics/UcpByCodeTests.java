package io.github.oliviercailloux.keyboardd.mnemonics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.MoreCollectors;
import org.junit.jupiter.api.Test;

public class UcpByCodeTests {
  public static int ucp(String s) {
    return s.codePoints().boxed().collect(MoreCollectors.onlyElement());
  }
  
  @Test
  public void testImplicit() {
    UcpByCode ucpByCode = UcpByCode.implicit();
    assertEquals(0x100, ucpByCode.ucp(0x01_000_100));
    assertEquals(0x200, ucpByCode.ucp(0x01_000_200));
    assertEquals(0x10F_FFF, ucpByCode.ucp(0x01_10F_FFF));
  }
  
  @Test
  public void testLatest() {
    Mnemonics mns = Mnemonics.latest();
    UcpByCode ucpByCode = UcpByCode.implicitAndExplicit(mns);
    assertEquals(0x100, ucpByCode.ucp(0x01_000_100));
    assertEquals(0x200, ucpByCode.ucp(0x01_000_200));
    assertEquals(0x10F_FFF, ucpByCode.ucp(0x01_10F_FFF));
    assertEquals(' ', ucpByCode.ucp(mns.canonical("space").code()));
    assertEquals('A', ucpByCode.ucp(mns.canonical("A").code()));
  }
}

package io.github.oliviercailloux.keyboardd.xkeys;

import static java.util.stream.IntStream.rangeClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class XkeysTests {
  @Test
  public void testLatest() throws Exception {
    ImmutableSortedSet<Short> expectedCodes = Stream
        .of(rangeClosed(9, 92), rangeClosed(94, 256), rangeClosed(360, 450), rangeClosed(452, 454),
            rangeClosed(456, 459), rangeClosed(472, 493), rangeClosed(505, 514),
            rangeClosed(520, 550), rangeClosed(568, 569), rangeClosed(584, 597),
            rangeClosed(600, 601), rangeClosed(616, 657), rangeClosed(664, 693),
            rangeClosed(696, 701), rangeClosed(704, 708))
        .flatMap(s -> s.boxed()).map(i -> i.shortValue())
        .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
    final int expectedAliasesSize = 47;

    Xkeys keys = Xkeys.latest();

    assertEquals(expectedCodes, keys.codes());

    assertEquals(expectedCodes.size(), keys.canonicals().size());
    assertTrue(keys.canonicals().contains("BKSL"));
    assertFalse(keys.canonicals().contains("AC12"));
    assertFalse(keys.canonicals().contains("Unkown"));

    assertEquals("LSGT", keys.canonical((short) 94));
    assertEquals("BKSL", keys.canonical((short) 51));
    assertThrows(IllegalArgumentException.class, () -> keys.canonical((short) 7));

    assertEquals(keys.canonicals(), keys.codeByCanonical().keySet());
    assertEquals(expectedCodes, keys.codeByCanonical().values());

    assertEquals(expectedCodes, keys.canonicalByCode().keySet());
    assertEquals(keys.canonicals(), keys.canonicalByCode().values());

    assertEquals(expectedAliasesSize, keys.aliases().size());
    assertFalse(keys.aliases().contains("BKSL"));
    assertTrue(keys.aliases().contains("AC12"));
    assertFalse(keys.aliases().contains("Unknown"));

    assertEquals(ImmutableSet.of(), keys.aliases("LSGT"));
    assertEquals(ImmutableSet.of("AC12"), keys.aliases("BKSL"));
    assertThrows(IllegalArgumentException.class, () -> keys.aliases("AC12"));
    assertThrows(IllegalArgumentException.class, () -> keys.aliases("Unknown"));

    assertEquals(keys.aliases(), keys.canonicalByAlias().keySet());
    assertTrue(keys.canonicals().containsAll(keys.canonicalByAlias().values()));

    assertEquals(expectedCodes.size() + expectedAliasesSize, keys.names().size());
    assertTrue(keys.names().contains("LSGT"));
    assertTrue(keys.names().contains("BKSL"));
    assertTrue(keys.names().contains("AC12"));
    assertFalse(keys.names().contains("Unknown"));

    assertFalse(keys.isAlias("LSGT"));
    assertFalse(keys.isAlias("BKSL"));
    assertTrue(keys.isAlias("AC12"));
    assertThrows(IllegalArgumentException.class, () -> keys.isAlias("Unknown"));

    assertEquals("LSGT", keys.canonical("LSGT"));
    assertEquals("BKSL", keys.canonical("BKSL"));
    assertEquals("BKSL", keys.canonical("AC12"));
    assertThrows(IllegalArgumentException.class, () -> keys.canonical("Unknown"));

    assertEquals(94, keys.code("LSGT"));
    assertEquals(49, keys.code("TLDE"));
    assertEquals(708, keys.code("I708"));
    assertEquals(51, keys.code("BKSL"));
    assertEquals(51, keys.code("AC12"));
    assertThrows(IllegalArgumentException.class, () -> keys.code("Unknown"));

    assertEquals(ImmutableSet.of("LSGT"), keys.names((short) 94));
    assertEquals(ImmutableSet.of("BKSL", "AC12"), keys.names((short) 51));
    assertThrows(IllegalArgumentException.class, () -> keys.names((short) 7));

    assertEquals(keys.names(), keys.codeByName().keySet());
    assertEquals(expectedCodes, ImmutableSortedSet.copyOf(keys.codeByName().values()));
  }

  @Test
  public void testWithoutAliases() throws Exception {
    Xkeys latest = Xkeys.latest();
    Xkeys withoutAliases = latest.withoutAliases();

    assertEquals(latest.codes(), withoutAliases.codes());

    assertEquals(latest.canonicals(), withoutAliases.canonicals());

    assertEquals("LSGT", withoutAliases.canonical((short) 94));
    assertEquals("BKSL", withoutAliases.canonical((short) 51));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.canonical((short) 7));

    assertEquals(latest.codeByCanonical(), withoutAliases.codeByCanonical());
    assertEquals(latest.canonicalByCode(), withoutAliases.canonicalByCode());

    assertTrue(withoutAliases.aliases().isEmpty());

    assertEquals(ImmutableSet.of(), withoutAliases.aliases("LSGT"));
    assertEquals(ImmutableSet.of(), withoutAliases.aliases("BKSL"));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.aliases("AC12"));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.aliases("Unknown"));

    assertTrue(withoutAliases.canonicalByAlias().isEmpty());

    assertEquals(latest.canonicals(), withoutAliases.names());

    assertFalse(withoutAliases.isAlias("LSGT"));
    assertFalse(withoutAliases.isAlias("BKSL"));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.isAlias("AC12"));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.isAlias("Unknown"));

    assertEquals("LSGT", withoutAliases.canonical("LSGT"));
    assertEquals("BKSL", withoutAliases.canonical("BKSL"));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.canonical("AC12"));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.canonical("Unknown"));

    assertEquals(94, withoutAliases.code("LSGT"));
    assertEquals(49, withoutAliases.code("TLDE"));
    assertEquals(708, withoutAliases.code("I708"));
    assertEquals(51, withoutAliases.code("BKSL"));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.code("AC12"));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.code("Unknown"));

    assertEquals(ImmutableSet.of("LSGT"), withoutAliases.names((short) 94));
    assertEquals(ImmutableSet.of("BKSL"), withoutAliases.names((short) 51));
    assertThrows(IllegalArgumentException.class, () -> withoutAliases.names((short) 7));

    assertEquals(latest.canonicals(), withoutAliases.codeByName().keySet());
    assertEquals(ImmutableSortedSet.copyOf(latest.codeByName().values()),
        ImmutableSortedSet.copyOf(withoutAliases.codeByName().values()));

    assertEquals(withoutAliases, withoutAliases.withoutAliases());
  }
}

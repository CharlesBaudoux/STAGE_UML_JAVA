package io.github.oliviercailloux.keyboardd.thekey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import io.github.oliviercailloux.keyboardd.mapping.KeyboardMap;
import io.github.oliviercailloux.keyboardd.mapping.XkbSymbolsReader;
import io.github.oliviercailloux.keyboardd.mnemonics.CanonicalKeyboardMap;
import io.github.oliviercailloux.keyboardd.mnemonics.CanonicalKeysymEntry;
import io.github.oliviercailloux.keyboardd.mnemonics.CanonicalMnemonic;
import io.github.oliviercailloux.keyboardd.mnemonics.Mnemonics;
import io.github.oliviercailloux.keyboardd.representable.CanonicalKeyboardMapRepresenter;
import io.github.oliviercailloux.keyboardd.representable.Representation;
import io.github.oliviercailloux.keyboardd.representable.SvgKeyboard;
import io.github.oliviercailloux.keyboardd.representable.SvgKeysymEntry;
import io.github.oliviercailloux.keyboardd.representable.SvgRepresentedKeyboard;
import io.github.oliviercailloux.keyboardd.representable.SvgXKey;
import io.github.oliviercailloux.keyboardd.representable.XKeyNamesAndRepresenter;
import io.github.oliviercailloux.keyboardd.xkeys.Xkeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class TheKeyMapTests {

  @Test
  public void writeMappedDefault() throws IOException {
    Document inputDocument = DomHelper.domHelper()
        .asDocument(Resources.asByteSource(Resources.getResource(getClass(), "The Key.svg")));
    SvgKeyboard inputSvg = SvgKeyboard.using(inputDocument);

    KeyboardMap map = XkbSymbolsReader.common().overwrite(XkbSymbolsReader.us());
    KeyboardMap canonicalized = map.canonicalize(Xkeys.latest().canonicalByAlias());
    Mnemonics mns = Mnemonics.latest();
    CanonicalKeyboardMap canonMap = CanonicalKeyboardMap.canonicalize(canonicalized, mns);
    CanonicalKeyboardMapRepresenter representer = CanonicalKeyboardMapRepresenter.from(canonMap,
        XKeyNamesAndRepresenter::defaultRepresentation);
    SvgRepresentedKeyboard represented = inputSvg.withCanonicalRepresentations(representer);
    ImmutableSet<String> xKeyNames = represented.xKeyNames();
    assertEquals(ImmutableSet.of("LCTL", "AB03", "AB04"), xKeyNames);

    SvgXKey svgL = Iterables.getOnlyElement(represented.svgXKeys("LCTL"));
    assertEquals("LCTL", svgL.xKeyName());
    assertEquals("(0.0, 0.0)", svgL.keyZone().topLeft().coordinates());
    assertEquals("(37.7953, 37.7953)", svgL.keyZone().bottomRight().coordinates());
    SvgKeysymEntry lCtlEntry = Iterables.getOnlyElement(svgL.svgKeysymEntries());
    assertEquals("Control_L", ((CanonicalMnemonic) lCtlEntry.canonicalKeysymEntry()).mnemonic());
    assertEquals(svgL, lCtlEntry.xKey());
    assertEquals("(0.0, 0.0)", lCtlEntry.zone().topLeft().coordinates());
    assertEquals("(37.7953, 37.7953)", lCtlEntry.zone().bottomRight().coordinates());

    SvgXKey svg3 = Iterables.getOnlyElement(represented.svgXKeys("AB03"));
    assertEquals("AB03", svg3.xKeyName());
    assertEquals("(45.3543, 0.0)", svg3.keyZone().topLeft().coordinates());
    assertEquals("(83.14959999999999, 37.7953)", svg3.keyZone().bottomRight().coordinates());
    ImmutableSortedSet<SvgKeysymEntry> ab03Entries = svg3.svgKeysymEntries();
    assertEquals(2, ab03Entries.size());
    UnmodifiableIterator<SvgKeysymEntry> ab3It = ab03Entries.iterator();
    SvgKeysymEntry ab3Entry1 = ab3It.next();
    assertEquals("c", ((CanonicalMnemonic) ab3Entry1.canonicalKeysymEntry()).mnemonic());
    assertEquals(svg3, ab3Entry1.xKey());
    assertEquals("(45.3543, 0.0)", ab3Entry1.zone().topLeft().coordinates());
    assertEquals("(64.25195, 37.7953)", ab3Entry1.zone().bottomRight().coordinates());
    SvgKeysymEntry ab3Entry2 = ab3It.next();
    assertEquals("C", ((CanonicalMnemonic) ab3Entry2.canonicalKeysymEntry()).mnemonic());
    assertEquals(svg3, ab3Entry2.xKey());
    assertEquals("(64.25195, 0.0)", ab3Entry2.zone().topLeft().coordinates());
    assertEquals("(83.14959999999999, 37.7953)", ab3Entry2.zone().bottomRight().coordinates());
    assertFalse(ab3It.hasNext());

    SvgXKey svg4 = Iterables.getOnlyElement(represented.svgXKeys("AB04"));
    assertEquals("AB04", svg4.xKeyName());
    assertEquals("(90.7087, 0.0)", svg4.keyZone().topLeft().coordinates());

    Document outputDocument = represented.document();
    String outputString = DomHelper.domHelper().toString(outputDocument);

    String expRes = "The Key with default common+us representations.svg";
    CharSource expectedOutput =
        Resources.asCharSource(getClass().getResource(expRes), StandardCharsets.UTF_8);
    assertEquals(expectedOutput.read(), outputString);
  }

  @Test
  public void writeMappedTweaked() throws IOException {
    Document inputDocument = DomHelper.domHelper()
        .asDocument(Resources.asByteSource(Resources.getResource(getClass(), "The Key.svg")));
    SvgKeyboard inputSvg = SvgKeyboard.using(inputDocument);

    KeyboardMap map = XkbSymbolsReader.common().overwrite(XkbSymbolsReader.us());
    CanonicalKeyboardMap canonMap = CanonicalKeyboardMap
        .canonicalize(map.canonicalize(Xkeys.latest().canonicalByAlias()), Mnemonics.latest());
    XKeyNamesAndRepresenter representer =
        CanonicalKeyboardMapRepresenter.from(canonMap, this::represent);
    Document outputDocument = inputSvg.withRepresentations(representer::representations);
    String outputString = DomHelper.domHelper().toString(outputDocument);

    String expRes = "The Key with tweaked common+us representations.svg";
    CharSource expectedOutput =
        Resources.asCharSource(getClass().getResource(expRes), StandardCharsets.UTF_8);
    assertEquals(expectedOutput.read(), outputString);
  }

  @Test
  public void writeMappedTweakedBigger() throws IOException {
    Document inputDocument = DomHelper.domHelper()
        .asDocument(Resources.asByteSource(Resources.getResource(getClass(), "The Key.svg")));
    SvgKeyboard inputSvg = SvgKeyboard.using(inputDocument);

    KeyboardMap map = XkbSymbolsReader.common().overwrite(XkbSymbolsReader.us());
    CanonicalKeyboardMap canonMap = CanonicalKeyboardMap
        .canonicalize(map.canonicalize(Xkeys.latest().canonicalByAlias()), Mnemonics.latest());
    XKeyNamesAndRepresenter representer =
        CanonicalKeyboardMapRepresenter.from(canonMap, this::represent);
    inputSvg.setFontSize(20);
    Document outputDocument = inputSvg.withRepresentations(representer::representations);
    String outputString = DomHelper.domHelper().toString(outputDocument);

    String expRes = "The Key with tweaked common+us representations bigger.svg";
    CharSource expectedOutput =
        Resources.asCharSource(getClass().getResource(expRes), StandardCharsets.UTF_8);
    assertEquals(expectedOutput.read(), outputString);
  }

  private Representation represent(CanonicalKeysymEntry entry) {
    if (entry instanceof CanonicalMnemonic mnemonic) {
      if (mnemonic.mnemonic().equals("Control_L")) {
        return Representation.fromString("Ctrl");
      }
    }
    return XKeyNamesAndRepresenter.defaultRepresentation(entry);
  }
}

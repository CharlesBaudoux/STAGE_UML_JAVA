package io.github.oliviercailloux.keyboardd.thekey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.github.oliviercailloux.jaris.io.PathUtils;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import io.github.oliviercailloux.keyboardd.representable.Representation;
import io.github.oliviercailloux.keyboardd.representable.SvgKeyboard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class TheKeyXKeyNamesTests {
  private DomHelper domHelper = DomHelper.domHelper();

  @Test
  public void writeWithXKeyNames() throws IOException {
    Document inputDocument = domHelper
        .asDocument(Resources.asByteSource(Resources.getResource(getClass(), "The Key.svg")));
    SvgKeyboard inputSvg = SvgKeyboard.using(inputDocument);
    Document outputDocument = inputSvg.withRepresentations(this::representName);
    String outputString = domHelper.toString(outputDocument);

    CharSource expectedOutput = Resources.asCharSource(
        getClass().getResource("The Key with X key names.svg"), StandardCharsets.UTF_8);
    assertEquals(expectedOutput.read(), outputString);
  }

  private ImmutableList<Representation> representName(String xKeyName) {
    return ImmutableList.of(Representation.fromString(xKeyName));
  }

  @Test
  public void writeWithChosenRepresentations() throws IOException {
    Document inputDocument = domHelper
        .asDocument(Resources.asByteSource(Resources.getResource(getClass(), "The Key.svg")));
    SvgKeyboard inputSvg = SvgKeyboard.using(inputDocument);
    inputSvg.setFontSize(25);
    Document outputDocument = inputSvg.withRepresentations(this::represent);
    String outputString = domHelper.toString(outputDocument);

    CharSource expectedOutput = Resources.asCharSource(
        getClass().getResource("The Key with chosen representations.svg"), StandardCharsets.UTF_8);
    assertEquals(expectedOutput.read(), outputString);
  }

  private ImmutableList<Representation> represent(String xKeyName) {
    final Representation repr = switch (xKeyName) {
      case "LCTL" -> Representation.fromSvg(logo());
      case "AB03" -> Representation.fromString("C");
      case "AB04" -> Representation.fromString("V");
      default -> Representation.fromString(xKeyName);
    };
    return ImmutableList.of(repr);
  }

  private Document logo() {
    try {
      return domHelper.asDocument(Resources
          .asByteSource(Resources.getResource(getClass(), "Logo stackoverflow image.svg")));
    } catch (IOException e) {
      throw new VerifyException(e);
    }
  }
}

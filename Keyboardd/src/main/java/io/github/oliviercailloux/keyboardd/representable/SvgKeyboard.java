package io.github.oliviercailloux.keyboardd.representable;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;

import static com.google.common.base.Verify.verify;

import com.google.common.base.MoreObjects;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.math.IntMath;
import io.github.oliviercailloux.geometry.Displacement;
import io.github.oliviercailloux.geometry.Point;
import io.github.oliviercailloux.geometry.Size;
import io.github.oliviercailloux.geometry.Zone;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import io.github.oliviercailloux.jaris.xml.XmlName;
import io.github.oliviercailloux.keyboardd.mnemonics.CanonicalKeysymEntry;
import io.github.oliviercailloux.svgb.RectangleElement;
import io.github.oliviercailloux.svgb.StyleElement;
import io.github.oliviercailloux.svgb.SvgDocumentHelper;
import io.github.oliviercailloux.svgb.SvgHelper;
import io.github.oliviercailloux.svgb.TextElement;
import java.math.RoundingMode;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

@UMLClass
public class SvgKeyboard {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(SvgKeyboard.class);

  private static DecimalFormat DECIMAL_FORMAT =
      new DecimalFormat("0.####", new DecimalFormatSymbols(Locale.US));

  private static final XmlName SVG_RECT_NAME =
      XmlName.expandedName(SvgDocumentHelper.SVG_NS_URI, "rect");

  public static final URI KEYBOARDD_NS = URI.create("https://io.github.oliviercailloux.keyboardd");
  private static final String KEYBOARDD_X_KEY_NAME_LOCAL_NAME = "x-key-name";
  public static final XmlName KEYBOARDD_X_KEY_NAME =
      XmlName.expandedName(KEYBOARDD_NS, KEYBOARDD_X_KEY_NAME_LOCAL_NAME);

  private static ImmutableList<Element> getElements(Element root, XmlName name) {
    if (name.namespace().isPresent()) {
      return DomHelper.toElements(
          root.getElementsByTagNameNS(name.namespace().get().toString(), name.localName()));
    }
    return DomHelper.toElements(root.getElementsByTagName(name.localName()));
  }

  /**
   * @deprecated Move to JARiS.
   */
  @Deprecated()
  private static void setAttribute(Element element, XmlName name, String value) {
    element.setAttributeNS(name.namespace().map(URI::toString).orElse(null), name.localName(),
        value);
  }

  private static record LineColDivision (int n, int nbCols) {
    public static LineColDivision forNb(int n) {
      /*
       * Given n the nuber of representations, we want to determine suitable values for x = nb
       * columns and y = nb lines (all three non negative integer values). We want to minimize the
       * number of lines without creating overly long lines (in other words, while keeping the
       * number of columns x within reasonable range), and therefore we opt for the minimal integer
       * value y such that x ≤ 2y. With 1 ≤ y lines, we need x = roundup(n / y) columns. Thus (if 1
       * ≤ n), we want the minimal y such that roundup(n / y) ≤ 2y. Note that roundup(n / y) ≤ 2y
       * iff n ≤ 2y². Thus, we want the minimal y such that n ≤ 2y², equivalently, such that y ≥
       * sqrt(n / 2). In other words, we want y = roundup(sqrt(n / 2)), and x = roundup(n / y).
       */
      int y = (int) Math.ceil(Math.sqrt(n / 2d));
      int x = y == 0 ? 0 : (int) Math.ceil(n / (double) y);
      verify(x <= 2 * y);
      /* Check that a smaller y is not suitable. */
      verify(Math.ceil(n / (double) (y - 1)) > 2 * (y - 1));
      return new LineColDivision(n, x);
    }

    LineColDivision {
      verify((n == 0) == (nbCols == 0));
    }

    int nbLines() {
      return nbCols == 0 ? 0 : IntMath.divide(n, nbCols, RoundingMode.CEILING);
    }

    int nbFullLines() {
      return nbCols == 0 ? 0 : IntMath.divide(n, nbCols, RoundingMode.FLOOR);
    }

    int nbColsOnShorterLine() {
      return n - nbCols * nbFullLines();
    }

    int nbCols(int lineNb) {
      return lineNb == 0 && hasShorterLine() ? nbColsOnShorterLine() : nbCols;
    }

    boolean hasShorterLine() {
      return nbColsOnShorterLine() > 0;
    }

    private ImmutableSortedSet<Zone> subZones(Zone entireZone) {
      Size subSize = subSize(entireZone);
      Point currentStartOfLine = entireZone.topLeft();
      ImmutableSortedSet.Builder<Zone> builder = ImmutableSortedSet.orderedBy(Comparator.comparing(
          Zone::topLeft,
          Comparator.comparing(Point::x).thenComparing(Comparator.comparing(Point::y).reversed())));
      for (int line = 0; line < nbLines(); ++line) {
        Point currentStart = currentStartOfLine;
        for (int col = 0; col < nbCols(line); ++col) {
          Zone subZone = Zone.at(currentStart, subSize);
          builder.add(subZone);
          currentStart = subZone.topRight();
        }
        currentStartOfLine = currentStartOfLine.plus(subSize.asDisplacement().vertical());
      }
      ImmutableSortedSet<Zone> subs = builder.build();
      verify(subs.size() == n);
      return subs;
    }

    public Size subSize(Zone entireZone) {
      Size scale = Size.given(1d / nbCols, 1d / nbLines());
      return entireZone.size().mult(scale);
    }
  }

  private static interface RepresentableSubZone {
    public Zone absoluteSubZone();

    /** A positive finite double if non-empty string; otherwise positive infinity. */
    public double maxWidthPerCp();

    public Element toSvg(SvgDocumentHelper h);
  }

  private static record SimpleRepresentableSubZone (Zone absoluteSubZone, Displacement shift,
      Representation repr) implements RepresentableSubZone {
    public Zone subZone() {
      return absoluteSubZone.move(shift);
    }

    @Override
    public double maxWidthPerCp() {
      if (!repr.isString()) {
        return Double.POSITIVE_INFINITY;
      }
      return subZone().size().mult(1d / repr.string().codePoints().count()).width();
    }

    @Override
    public Element toSvg(SvgDocumentHelper h) {
      if (repr.isString()) {
        return toStringSvg(h);
      }
      return toReprSvg(h);
    }

    private Element toStringSvg(SvgDocumentHelper h) {
      return h.text().setBaselineStart(subZone().center()).setContent(repr.string()).element();
    }

    private Element toReprSvg(SvgDocumentHelper h) {
      Element svg = repr.svg().getDocumentElement();
      Optional<Size> svgSizeOpt = SvgHelper.tryGetSize(svg);
      Element importedSvg = (Element) h.document().importNode(svg, true);
      Size svgSizeMaxSubZone;
      if (svgSizeOpt.isEmpty()) {
        svgSizeMaxSubZone = subZone().size();
      } else {
        Size svgSize = svgSizeOpt.orElseThrow(VerifyException::new);
        if (svgSize.width() > subZone().size().width()
            || svgSize.height() > subZone().size().height()) {
          svgSizeMaxSubZone = subZone().size();
        } else {
          svgSizeMaxSubZone = svgSize;
        }
      }
      // Point gap = subZone.size().plus(svgSizeMaxSubZone.opposite());
      // Point halfGap = gap.mult(0.5d);
      // Point elemPos = subZone.start().plus(halfGap);
      Zone posAndSize = Zone.enclosing(subZone().center()).resizedFixedCenter(svgSizeMaxSubZone);
      SvgHelper.setPosition(importedSvg, posAndSize.topLeft());
      SvgHelper.setSize(importedSvg, svgSizeMaxSubZone);
      return importedSvg;
    }
  }

  private static class CanonicRepresentableSubZone implements RepresentableSubZone {
    public static CanonicRepresentableSubZone from(Zone absoluteSubZone, Displacement shift,
        CanonicalKeysymEntry c, Representation repr) {
      return new CanonicRepresentableSubZone(absoluteSubZone, shift, c, repr);
    }

    private final SimpleRepresentableSubZone delegate;
    private final CanonicalKeysymEntry c;

    private CanonicRepresentableSubZone(Zone absoluteSubZone, Displacement shift,
        CanonicalKeysymEntry c, Representation repr) {
      this.delegate = new SimpleRepresentableSubZone(absoluteSubZone, shift, repr);
      this.c = c;
    }

    public CanonicalKeysymEntry c() {
      return c;
    }

    @Override
    public Zone absoluteSubZone() {
      return delegate.absoluteSubZone();
    }

    @Override
    public double maxWidthPerCp() {
      return delegate.maxWidthPerCp();
    }

    @Override
    public Element toSvg(SvgDocumentHelper h) {
      return delegate.toSvg(h);
    }

    @Override
    public boolean equals(Object o2) {
      if (!(o2 instanceof CanonicRepresentableSubZone)) {
        return false;
      }
      final CanonicRepresentableSubZone t2 = (CanonicRepresentableSubZone) o2;
      return delegate.equals(t2.delegate) && c.equals(t2.c);
    }

    @Override
    public int hashCode() {
      return Objects.hash(delegate, c);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("delegate", delegate).add("c", c).toString();
    }
  }

  private static interface RepresentableZone {
    public String xKeyName();

    public ImmutableList<Representation> reprs();

    public ImmutableSet<? extends RepresentableSubZone> subRepresentables(Displacement shift);

    public double maxWidthPerCp();

    public RectangleElement rectangle();
  }

  // private static class CanonicalRepresentableZone implements RepresentableZone {
  // public static CanonicalRepresentableZone from (String xKeyName, RectangleElement rectangle,
  // ImmutableList<CanonicalKeysymEntry> entries, Function<CanonicalKeysymEntry, Representation>
  // representer) {
  // ImmutableList<Representation> reprs =
  // entries.stream().map(representer).collect(ImmutableList.toImmutableList());
  // return new RepresentableZoneRecord(xKeyName, rectangle, reprs);
  // }
  // }

  private static record GenericRepresentableZone<T> (String xKeyName, RectangleElement rectangle,
      ImmutableList<T> data, Function<T, Representation> repr) implements RepresentableZone {
    public static RepresentableZone from(String xKeyName, RectangleElement rectangle,
        ImmutableList<Representation> reprs) {
      return new GenericRepresentableZone<>(xKeyName, rectangle, reprs, Function.identity());
    }

    @Override
    public ImmutableList<Representation> reprs() {
      return data.stream().map(repr).collect(ImmutableList.toImmutableList());
    }

    private LineColDivision div() {
      return LineColDivision.forNb(data.size());
    }

    @Override
    public ImmutableSet<RepresentableSubZone> subRepresentables(Displacement shift) {
      ImmutableSortedSet<Zone> subZones = div().subZones(rectangle.zone());
      UnmodifiableIterator<T> dIt = data.iterator();
      final ImmutableSet.Builder<RepresentableSubZone> subRepresentables =
          new ImmutableSet.Builder<>();
      for (Zone subZone : subZones) {
        T t = dIt.next();
        Representation r = repr.apply(t);
        if (t instanceof CanonicalKeysymEntry c) {
          subRepresentables.add(CanonicRepresentableSubZone.from(subZone, shift, c, r));
        } else {
          subRepresentables.add(new SimpleRepresentableSubZone(subZone, shift, r));
        }
      }
      verify(!dIt.hasNext());
      return subRepresentables.build();
    }

    @Override
    public double maxWidthPerCp() {
      return subRepresentables(Displacement.noMove()).stream()
          .mapToDouble(RepresentableSubZone::maxWidthPerCp).min().orElse(Double.POSITIVE_INFINITY);
    }
  }

  private static class CanonicRepresentableZone implements RepresentableZone {
    public static CanonicRepresentableZone from(String xKeyName, RectangleElement rectangle,
        ImmutableList<CanonicalKeysymEntry> entries,
        Function<CanonicalKeysymEntry, Representation> representer) {
      return new CanonicRepresentableZone(xKeyName, rectangle, entries, representer);
    }

    private final GenericRepresentableZone<CanonicalKeysymEntry> delegate;

    private CanonicRepresentableZone(String xKeyName, RectangleElement rectangle,
        ImmutableList<CanonicalKeysymEntry> entries,
        Function<CanonicalKeysymEntry, Representation> representer) {
      this.delegate = new GenericRepresentableZone<>(xKeyName, rectangle, entries, representer);
    }

    @Override
    public String xKeyName() {
      return delegate.xKeyName;
    }

    @Override
    public ImmutableList<Representation> reprs() {
      return delegate.reprs();
    }

    @Override
    public ImmutableSet<CanonicRepresentableSubZone> subRepresentables(Displacement shift) {
      return delegate.subRepresentables(shift).stream().map(z -> (CanonicRepresentableSubZone) z)
          .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public double maxWidthPerCp() {
      return delegate.maxWidthPerCp();
    }

    @Override
    public RectangleElement rectangle() {
      return delegate.rectangle;
    }
  }

  /**
   * 1 unit in the given physical keyboard is rendered as 1 cm at 96 DPI (thus as 96/2.54 ≅ 38 dots)
   */
  @UMLInclude
  public static SvgKeyboard zonedFrom(RectangularKeyboard physicalKeyboard) {
    final DomHelper d = DomHelper.domHelper();
    final SvgDocumentHelper h = SvgDocumentHelper.using(d);
    final Document doc = h.document();
    doc.getDocumentElement().setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:kdd",
        KEYBOARDD_NS.toString());

    double dpi = 96d;
    double dotsPerCm = dpi / 2.54d;
    h.setSize(physicalKeyboard.zone().size().mult(dotsPerCm));

    for (RectangularKey key : physicalKeyboard.keys()) {
      Point posScaled = key.zone().topLeft().mult(dotsPerCm);
      Size sizeScaled = key.zone().size().mult(dotsPerCm);
      RectangleElement rect = h.rectangle(Zone.at(posScaled, sizeScaled)).setRounding(10d);
      String xKeyName = key.xKeyName();
      if (!xKeyName.isEmpty()) {
        setAttribute(rect.element(), KEYBOARDD_X_KEY_NAME, xKeyName);
      }
      doc.getDocumentElement().appendChild(rect.element());
    }

    // return new SvgKeyboard(doc).withRepresentations(k ->
    // ImmutableList.of(Representation.fromString(k)));
    SvgKeyboard kb = new SvgKeyboard(h);

    String inner = """
        fill-opacity: 0;
        stroke: black;
        stroke-width: 1px;""";
    kb.appendStyle(RectangleElement.NODE_NAME, inner);
    return kb;
  }

  @UMLInclude
  public static SvgKeyboard using(Document doc) {
    return new SvgKeyboard(SvgDocumentHelper.using(doc));
  }

  private final SvgDocumentHelper h;
  private double fontSize = Double.NaN;

  private SvgKeyboard(SvgDocumentHelper h) {
    this.h = h;
  }

  public Document document() {
    return h.document();
  }

  private void appendStyle(String element, String inner) {
    String content = element + " {\n" + inner + "\n}";
    StyleElement style = h.style().setContent(content);
    h.document().getDocumentElement().insertBefore(style.element(),
        h.document().getDocumentElement().getFirstChild());
  }

  @UMLInclude
  public ImmutableMap<RectangleElement, String> keyBindingZonesToXKeyName() {
    ImmutableMap.Builder<RectangleElement, String> reprsBuilder = ImmutableMap.builder();
    for (Element rect : getElements(h.document().getDocumentElement(), SVG_RECT_NAME)) {
      if (!DomHelper.hasAttribute(rect, KEYBOARDD_X_KEY_NAME)) {
        continue;
      }
      String xKeyName = DomHelper.getAttribute(rect, KEYBOARDD_X_KEY_NAME);
      reprsBuilder.put(RectangleElement.using(rect), xKeyName);
    }
    return reprsBuilder.build();
  }

  public double maxWidthPerCp(Function<String, ? extends List<String>> descriptionsByXKeyName) {
    XKeyNamesRepresenter representationsByXKeyName = s -> descriptionsByXKeyName.apply(s).stream()
        .map(Representation::fromString).collect(ImmutableList.toImmutableList());
    ImmutableSet<? extends RepresentableZone> zones = getZones(representationsByXKeyName);
    return maxWidthPerCp(zones);
  }

  private double maxWidthPerCp(Set<? extends RepresentableZone> zones) {
    return zones.stream().mapToDouble(t -> t.maxWidthPerCp()).min()
        .orElse(Double.POSITIVE_INFINITY);
  }

  /** NaN for maxWidthPerCp (default) */
  @UMLInclude
  public SvgKeyboard setFontSize(double fontSize) {
    this.fontSize = fontSize;
    return this;
  }

  private double fontSize(Set<? extends RepresentableZone> zones) {
    if (!Double.isNaN(fontSize)) {
      return fontSize;
    }
    return maxWidthPerCp(zones);
  }

  /**
   * Adds representations to the zones found in this document, according to the given function.
   *
   * @param representationsByXKeyName the respective representations to add to the zones.
   * @return the document with the added representations.
   */
  @UMLInclude
  public Document withRepresentations(XKeyNamesRepresenter representationsByXKeyName) {
    ImmutableSet<SvgXKey> svgKeys = withRepresentationsInternal(representationsByXKeyName);
    if (!(representationsByXKeyName instanceof CanonicalKeyboardMapRepresenter)) {
      verify(svgKeys.isEmpty());
    }
    return h.document();
  }

  @UMLInclude
  public SvgRepresentedKeyboard
      withCanonicalRepresentations(CanonicalKeyboardMapRepresenter representer) {
    ImmutableSet<SvgXKey> svgKeys = withRepresentationsInternal(representer);
    return SvgRepresentedKeyboard.given(this, svgKeys);
  }

  public ImmutableSet<SvgXKey>
      withRepresentationsInternal(XKeyNamesRepresenter representationsByXKeyName) {
    ImmutableSet<? extends RepresentableZone> zones = getZones(representationsByXKeyName);
    // it’s very unlikely that the font size will be constrained in height, so let’s just consider
    // the available width. We consider that 1px font size (which determines the height of am em
    // box) is about a 1px car wide. A very rough approximation, to be sure.
    double effectiveFontSize = fontSize(zones);
    if (Double.isFinite(effectiveFontSize)) {
      String inner = """
          text-anchor: middle;
          dominant-baseline: middle;
          font-size: %spx;""".formatted(DECIMAL_FORMAT.format(effectiveFontSize));
      appendStyle(TextElement.NODE_NAME, inner);
    }

    final ImmutableSet.Builder<SvgXKey> svgKeys = new ImmutableSet.Builder<>();
    for (RepresentableZone zone : zones) {
      Displacement shift = Displacement.between(Point.origin(), zone.rectangle().zone().topLeft());
      Element g = h.g().translate(shift).getElement();
      Node next = zone.rectangle().element().getNextSibling();
      zone.rectangle().element().getParentNode().insertBefore(g, next);

      if (zone instanceof CanonicRepresentableZone c) {
        SvgXKey svgXKey = SvgXKey.create(c.xKeyName(), zone.rectangle().zone(), zone.rectangle());
        final ImmutableSet.Builder<SvgKeysymEntry> entries = new ImmutableSet.Builder<>();
        for (CanonicRepresentableSubZone r : c.subRepresentables(shift.opposite())) {
          Element svgRepr = r.toSvg(h);
          g.appendChild(svgRepr);
          entries.add(new SvgKeysymEntry(r.c(), r.absoluteSubZone(), svgRepr, svgXKey));
        }
        svgXKey.setContent(entries.build());
        svgKeys.add(svgXKey);
      } else {
        for (RepresentableSubZone r : zone.subRepresentables(shift.opposite())) {
          Element svgRepr = r.toSvg(h);
          g.appendChild(svgRepr);
        }
      }
    }
    return svgKeys.build();
  }

  private ImmutableSet<? extends RepresentableZone>
      getZones(XKeyNamesRepresenter representationsByXKeyName) {
    ImmutableSet<? extends RepresentableZone> zones =
        keyBindingZonesToXKeyName().keySet().stream().map(rect -> {
          String xKeyName = DomHelper.getAttribute(rect.element(), KEYBOARDD_X_KEY_NAME);
          if (representationsByXKeyName instanceof CanonicalKeyboardMapRepresenter c) {
            ImmutableList<CanonicalKeysymEntry> entries = c.entries(xKeyName);
            return CanonicRepresentableZone.from(xKeyName, rect, entries, c::representation);
          }
          List<Representation> reprs = representationsByXKeyName.representations(xKeyName);
          return GenericRepresentableZone.from(xKeyName, rect, ImmutableList.copyOf(reprs));
        }).collect(ImmutableSet.toImmutableSet());
    return zones;
  }
}

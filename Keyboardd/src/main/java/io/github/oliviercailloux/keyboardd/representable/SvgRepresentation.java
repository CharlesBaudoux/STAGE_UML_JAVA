package io.github.oliviercailloux.keyboardd.representable;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import org.w3c.dom.Document;

/** A key displaying an SVG document. */
@UMLClass
public record SvgRepresentation (Document svg) implements Representation {
  public SvgRepresentation {
    checkNotNull(svg);
    // Le document est copie a l'entree, comme il l'etait dans la classe unique : un Document est
    // mutable, et une representation ne doit pas changer sous les pieds de qui la detient.
    svg = (Document) svg.cloneNode(true);
  }

  // equals et hashCode sont redefinis parce que ceux d'un record compareraient les Document par
  // identite. La comparaison structurelle est celle qu'avait deja la classe unique.
  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof SvgRepresentation t2)) {
      return false;
    }
    return svg.isEqualNode(t2.svg);
  }

  @Override
  public int hashCode() {
    return svg.hashCode();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("doc", DomHelper.toDebugString(svg)).toString();
  }
}

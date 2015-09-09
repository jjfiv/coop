package edu.umass.cs.jfoley.coop.index.general;

import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.MappingCoder;

import javax.annotation.Nonnull;

/**
 * @author jfoley
 */
public class NamespacedLabel implements Comparable<NamespacedLabel> {
  private final String fullString;

  public NamespacedLabel(String namespace, String label) {
    this.fullString = namespace + ":" + label;
  }

  @Override
  public String toString() {
    return fullString;
  }

  public static NamespacedLabel fromString(String x) {
    return new NamespacedLabel(
        StrUtil.takeBefore(x, ":"),
        StrUtil.takeAfter(x, ":"));
  }

  public static Coder<NamespacedLabel> coder = new MappingCoder<>(
      NamespacedLabel.class,
      CharsetCoders.utf8,
      NamespacedLabel::toString,
      NamespacedLabel::fromString
  );

  @Override
  public int hashCode() {
    return fullString.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof NamespacedLabel &&
        fullString.equals(((NamespacedLabel) other).fullString);
  }

  @Override
  public int compareTo(@Nonnull NamespacedLabel o) {
    return toString().compareTo(o.toString());
  }
}

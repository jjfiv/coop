package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.MappingCoder;

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
      CharsetCoders.utf8Raw,
      NamespacedLabel::toString,
      NamespacedLabel::fromString
  );

  @Override
  public int compareTo(NamespacedLabel o) {
    return toString().compareTo(o.toString());
  }
}

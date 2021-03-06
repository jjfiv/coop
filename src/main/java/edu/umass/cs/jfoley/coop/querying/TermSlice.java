package edu.umass.cs.jfoley.coop.querying;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * @author jfoley
 */
public class TermSlice implements Comparable<TermSlice>, Serializable {
  public final int document;
  public final int start;
  public final int end;
  public TermSlice(int document, int start, int end) {
    this.document = document;
    this.start = start;
    this.end = end;
  }

  @Override
  public int compareTo(@Nonnull TermSlice o) {
    int cmp = Integer.compare(document, o.document);
    if(cmp != 0) return cmp;
    cmp = Integer.compare(start, o.start);
    return cmp;
  }

  @Override
  public String toString() {
    return "D"+document+" ["+start+","+end+")";
  }

  public int size() { return end - start; }
}

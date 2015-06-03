package edu.umass.cs.jfoley.coop.querying;

/**
 * @author jfoley
 */
public class TermSlice implements Comparable<TermSlice> {
  public final int document;
  public final int start;
  public final int end;
  public TermSlice(int document, int start, int end) {
    this.document = document;
    this.start = start;
    this.end = end;
  }

  @Override
  public int compareTo(TermSlice o) {
    int cmp = Integer.compare(document, o.document);
    if(cmp != 0) return cmp;
    cmp = Integer.compare(start, o.start);
    return cmp;
  }
}

package edu.umass.cs.jfoley.coop.querying;

/**
 * @author jfoley
 */
public class TermSlice {
  public final int document;
  public final int start;
  public final int end;
  public TermSlice(int document, int start, int end) {
    this.document = document;
    this.start = start;
    this.end = end;
  }
}

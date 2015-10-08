package edu.umass.cs.jfoley.coop.pagerank;

import javax.annotation.Nonnull;

/**
 * This class holds document identifier and score information.
 * @author jfoley
 */
public class ScoredDoc implements Comparable<ScoredDoc> {
  public final double score;
  public final int id;

  public ScoredDoc(double score, int id) {
    this.score = score;
    this.id = id;
  }

  @Override
  public int compareTo(@Nonnull ScoredDoc o) {
    // Java always does ascending order, so make this sort in descending order :)
    return -Double.compare(this.score, o.score);
  }
}

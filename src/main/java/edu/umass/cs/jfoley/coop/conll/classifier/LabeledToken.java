package edu.umass.cs.jfoley.coop.conll.classifier;

/**
 * @author jfoley.
 */
public class LabeledToken {
  public final long time; // System.currentTimeMillis();
  public final int tokenId;
  public final boolean positive;

  public LabeledToken() {
    this(0, -1, false);
  }
  public LabeledToken(long time, int tokenId, boolean positive) {
    this.time = time;
    this.tokenId = tokenId;
    this.positive = positive;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(tokenId);
  }

  @Override
  public boolean equals(Object other) {
    if(other instanceof LabeledToken) {
      LabeledToken rhs = (LabeledToken) other;
      return this.tokenId == rhs.tokenId && this.positive == rhs.positive && this.time == rhs.time;
    }
    return false;
  }
}

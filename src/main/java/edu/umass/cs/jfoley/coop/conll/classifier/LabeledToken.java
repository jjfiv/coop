package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.ArrayListMap;
import ciir.jfoley.chai.lang.annotations.UsedByReflection;
import org.lemurproject.galago.utility.Parameters;

/**
 * @author jfoley.
 */
public class LabeledToken {
  public final long time; // System.currentTimeMillis();
  public final int tokenId;
  public final boolean positive;

  @UsedByReflection
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

  public Parameters toJSON() {
    ArrayListMap<String,Object> data = new ArrayListMap<>();
    data.put("time", time);;
    data.put("tokenId", tokenId);;
    data.put("positive", positive);;
    return Parameters.wrap(data);
  }
}

package edu.umass.cs.jfoley.coop.conll.classifier;

import edu.umass.cs.jfoley.coop.document.CoopToken;
import org.lemurproject.galago.utility.lists.Scored;

/**
 * @author jfoley.
 */
public class ClassifiedToken extends Scored {
  public int classifierId; // name of the classifier
  public boolean positive; // threshold-applied score
  public CoopToken token; // token-instance.
  public int tokenId; // token id

  public ClassifiedToken(int classifierId, boolean positive, double score, CoopToken token) {
    super(score);
    this.classifierId = classifierId;
    this.positive = positive;
    this.score = score;
    this.token = token;
    this.tokenId = token.id();
  }

  public int getTokenId() {
    return tokenId;
  }

  @Override
  public ClassifiedToken clone(double score) {
    return new ClassifiedToken(classifierId, positive, score, token);
  }
}

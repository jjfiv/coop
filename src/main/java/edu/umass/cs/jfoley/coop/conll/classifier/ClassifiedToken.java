package edu.umass.cs.jfoley.coop.conll.classifier;

import edu.umass.cs.jfoley.coop.conll.SentenceIndexedToken;
import org.lemurproject.galago.utility.lists.Scored;

/**
 * @author jfoley.
 */
public class ClassifiedToken extends Scored {
  public final String classifierName; // name of the classifier
  public final boolean positive; // threshold-applied score
  public final SentenceIndexedToken token; // token-instance.

  public ClassifiedToken(String classifierName, boolean positive, double score, SentenceIndexedToken token) {
    super(score);
    this.classifierName = classifierName;
    this.positive = positive;
    this.score = score;
    this.token = token;
  }

  public int getTokenId() {
    return token.tokenId;
  }

  @Override
  public ClassifiedToken clone(double score) {
    return new ClassifiedToken(classifierName, positive, score, token);
  }
}

package edu.umass.cs.jfoley.coop.conll;

import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author jfoley
 */
public class SentenceIndexedToken implements Comparable<SentenceIndexedToken> {
  public int sentenceId;
  public int tokenId;
  public Map<String, String> terms;
  public Set<String> indicators;

  public SentenceIndexedToken() {
    sentenceId = -1;
    tokenId = -1;
    terms = new HashMap<>();
    indicators = new HashSet<>();
  }

  public SentenceIndexedToken(int sentenceId, int tokenId) {
    this();
    this.sentenceId = sentenceId;
    this.tokenId = tokenId;
  }

  @Override
  public int compareTo(@Nonnull SentenceIndexedToken o) {
    return Integer.compare(tokenId, o.tokenId);
  }

  public Parameters toJSON() {
    Parameters p = Parameters.create();
    p.put("sentenceId", sentenceId);
    p.put("tokenId", tokenId);
    p.put("terms", Parameters.wrap(terms));
    p.put("indicators", new ArrayList<>(indicators));
    return p;
  }

  public Map<String, String> getTerms() {
    return terms;
  }

  public Set<String> getIndicators() {
    return indicators;
  }
}

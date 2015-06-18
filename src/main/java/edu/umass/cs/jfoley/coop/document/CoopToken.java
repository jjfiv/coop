package edu.umass.cs.jfoley.coop.document;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author jfoley
 */
public class CoopToken implements Comparable<CoopToken> {
  int document;
  int index;
  Map<String,String> terms;
  Set<String> indicators;
  // TODO? Set<String> enclosingTags;

  public CoopToken() {
    document = -1;
    index = -1;
    terms = new HashMap<>();
    indicators = new HashSet<>();
  }

  public CoopToken(int document, int index) {
    this();
    this.document = document;
    this.index = index;
  }

  @Override
  public int compareTo(@Nonnull CoopToken o) {
    int cmp = Integer.compare(document, o.document);
    if(cmp != 0) return cmp;
    return Integer.compare(index, o.index);
  }

  public Set<String> getIndicators() {
    return indicators;
  }

  public Map<String, String> getTerms() {
    return terms;
  }
}

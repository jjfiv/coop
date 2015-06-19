package edu.umass.cs.jfoley.coop.conll.classifier;

import java.util.HashSet;
import java.util.Set;

/**
 * @author jfoley
 */
public class ClassifiedData {
  public Set<String> positiveExamples;
  public Set<String> negativeExamples;

  public ClassifiedData() {
    this(new HashSet<>(), new HashSet<>());
  }

  public ClassifiedData(Set<String> positiveExamples, Set<String> negativeExamples) {
    this.positiveExamples = positiveExamples;
    this.negativeExamples = negativeExamples;
  }
}

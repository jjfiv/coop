package edu.umass.cs.jfoley.coop.conll.classifier;

import java.util.List;

/**
 * @author jfoley.
 */
public abstract class Classifier {
  protected final int numFeatures;

  public Classifier(int numFeatures) {
    this.numFeatures = numFeatures;
  }

  abstract double train(List<? extends FeatureVector> pos, List<? extends FeatureVector> neg);

  abstract boolean predict(FeatureVector fv);

  /** return a raw score */
  abstract double rank(FeatureVector fv);

  /** return number of features used. */
  abstract int getComplexity();
}

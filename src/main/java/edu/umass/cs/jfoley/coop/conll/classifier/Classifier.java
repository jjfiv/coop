package edu.umass.cs.jfoley.coop.conll.classifier;

import gnu.trove.map.hash.TIntFloatHashMap;

import java.util.List;

/**
 * @author jfoley.
 */
public abstract class Classifier {
  protected final int numFeatures;

  public Classifier(int numFeatures) {
    this.numFeatures = numFeatures;
  }

  public abstract double train(List<? extends FeatureVector> pos, List<? extends FeatureVector> neg);

  public abstract boolean predict(FeatureVector fv);

  /** return a raw score */
  public abstract double rank(FeatureVector fv);

  /** return number of features used. */
  public abstract int getComplexity();

  /** return the actual features used: */
  public abstract TIntFloatHashMap getSparseFeatures();
}

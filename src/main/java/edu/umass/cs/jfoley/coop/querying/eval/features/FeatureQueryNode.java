package edu.umass.cs.jfoley.coop.querying.eval.features;

import edu.umass.cs.ciir.waltz.feature.Feature;
import edu.umass.cs.jfoley.coop.querying.eval.QueryEvalNode;

/**
 * A leaf query node built on Waltz's Feature abstraction. Note that they may be MoverFeatures, but they should never move from here.
 * @param <X> type of feature.
 * @see Feature
 */
public abstract class FeatureQueryNode<X> implements QueryEvalNode<X> {
  protected Feature<X> feature;
  protected FeatureQueryNode(Feature<X> feature) {
    this.feature = feature;
  }
  protected boolean hasFeature(int document) {
    return feature.hasFeature(document);
  }
  protected X getFeature(int document) {
    return feature.getFeature(document);
  }
}

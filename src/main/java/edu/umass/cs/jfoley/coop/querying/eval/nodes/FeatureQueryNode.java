package edu.umass.cs.jfoley.coop.querying.eval.nodes;

import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.feature.Feature;
import edu.umass.cs.jfoley.coop.querying.eval.QueryEvalNode;

import javax.annotation.Nullable;

/**
 * A leaf query node built on Waltz's Feature abstraction. Note that they may be MoverFeatures, but they should never move from here.
 * @param <X> type of feature.
 * @see Feature
 */
public final class FeatureQueryNode<X> implements QueryEvalNode<X> {
  protected Feature<X> feature;
  protected FeatureQueryNode(Feature<X> feature) {
    this.feature = feature;
  }
  public FeatureQueryNode(PostingMover<X> mover) {
    this.feature = mover.getFeature();
  }

  @Nullable
  @Override
  public X calculate(int document) {
    return feature.getFeature(document);
  }
}

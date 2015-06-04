package edu.umass.cs.jfoley.coop.querying.eval.features;

import edu.umass.cs.ciir.waltz.feature.Feature;

import javax.annotation.Nullable;

/**
 * @author jfoley
 */
public class FeatureCountNode extends FeatureQueryNode<Integer> {
  protected FeatureCountNode(Feature<Integer> feature) {
    super(feature);
  }

  @Nullable
  public Integer calculate(int document) {
    return getFeature(document);
  }
}

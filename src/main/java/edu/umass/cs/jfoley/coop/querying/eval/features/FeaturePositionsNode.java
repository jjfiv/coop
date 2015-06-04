package edu.umass.cs.jfoley.coop.querying.eval.features;

import edu.umass.cs.ciir.waltz.feature.Feature;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;

import javax.annotation.Nullable;

/**
 * @author jfoley
 */
public class FeaturePositionsNode extends FeatureQueryNode<PositionsList> {
  public FeaturePositionsNode(Feature<PositionsList> feature) {
    super(feature);
  }

  @Nullable
  @Override
  public PositionsList calculate(int document) {
    return getFeature(document);
  }
}

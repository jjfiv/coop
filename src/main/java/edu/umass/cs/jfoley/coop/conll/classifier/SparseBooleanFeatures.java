package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;

import java.util.Iterator;

/**
 * @author jfoley.
 */
public class SparseBooleanFeatures implements FeatureVector {
  final IntList active;

  public SparseBooleanFeatures(IntList active) {
    this.active = active;
  }

  @Override
  public Iterator<Pair<Integer, Float>> iterator() {
    return IterableFns.map(active, (idx) -> Pair.of(idx, 1.0f)).iterator();
  }
}

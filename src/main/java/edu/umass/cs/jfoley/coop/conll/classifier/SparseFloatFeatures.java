package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.Pair;
import gnu.trove.map.hash.TIntFloatHashMap;

import java.util.Iterator;

/**
 * @author jfoley.
 */
public class SparseFloatFeatures implements FeatureVector {
  TIntFloatHashMap features;

  @Override
  public Iterator<Pair<Integer, Float>> iterator() {
    final int[] keys = features.keys();
    return new Iterator<Pair<Integer, Float>>() {
      int pos = 0;

      @Override
      public boolean hasNext() {
        return pos < keys.length;
      }

      @Override
      public Pair<Integer, Float> next() {
        float val = features.get(keys[pos]);
        return Pair.of(pos++, val);
      }
    };
  }
}

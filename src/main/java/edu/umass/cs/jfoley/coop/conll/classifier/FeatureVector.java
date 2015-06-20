package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.Pair;

import java.util.Iterator;

/**
 * @author jfoley.
 */
public interface FeatureVector extends Iterable<Pair<Integer, Float>> {
  Iterator<Pair<Integer, Float>> iterator();
}

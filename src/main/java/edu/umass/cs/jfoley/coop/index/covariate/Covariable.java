package edu.umass.cs.jfoley.coop.index.covariate;

import ciir.jfoley.chai.collections.Pair;

import javax.annotation.Nonnull;

/**
 * A pair of variables that can be ordered from left-to-right.
 * @author jfoley
 */
public class Covariable<A extends Comparable<A>, B extends Comparable<B>> extends Pair<A, B> implements Comparable<Covariable<A, B>> {
  public Covariable(A left, B right) {
    super(left, right);
  }

  @Override
  public int compareTo(@Nonnull Covariable<A, B> o) {
    int cmp = left.compareTo(o.left);
    if (cmp != 0) return cmp;

    return right.compareTo(o.right);
  }
}

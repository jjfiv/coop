package edu.umass.cs.jfoley.coop.querying.eval.nodes;

import edu.umass.cs.jfoley.coop.querying.eval.QueryEvalNode;

import javax.annotation.Nonnull;

/**
 * @author jfoley
 */
public abstract class MappingQueryNode<A, B> implements QueryEvalNode<B> {
  public final QueryEvalNode<A> inner;

  protected MappingQueryNode(QueryEvalNode<A> inner) {
    this.inner = inner;
  }

  public abstract B map(@Nonnull A input);

  @Override
  public B calculate(int document) {
    A innerValue = inner.calculate(document);
    if (innerValue == null) return null;
    return map(innerValue);
  }
}

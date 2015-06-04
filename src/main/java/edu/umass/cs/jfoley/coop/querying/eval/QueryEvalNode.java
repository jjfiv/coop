package edu.umass.cs.jfoley.coop.querying.eval;

import javax.annotation.Nullable;

/**
 * Unlike Galago, our calculation is nullable, and generic.
 * Nullability gives us the ability to only calculate matches in one place.
 * Genericity gives us the ability to have our processing return anything if there's a node for it. Galago makes the assumption that the top level is a double, so if you can't squeeze it into a double you can't use it.
 * @param <T> the type of value to collect per document.
 * @see QueryEvalEngine
 * @see DocumentResult
 */
public interface QueryEvalNode<T> {
  @Nullable
  T calculate(int document);
}

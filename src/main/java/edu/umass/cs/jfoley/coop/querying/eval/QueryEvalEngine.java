package edu.umass.cs.jfoley.coop.querying.eval;

import ciir.jfoley.chai.fn.SinkFn;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class QueryEvalEngine {
  public static <T> List<DocumentResult<T>> Evaluate(Mover mover, QueryEvalNode<T> query) {
    ArrayList<DocumentResult<T>> results = new ArrayList<>();
    Evaluate(mover, query, results::add);
    return results;
  }

  /**
   * An evaluation of a query given a mover that feeds results into the output sink.
   * @param mover an object that decides which documents to evaluate the query on.
   * @param query a query to calculate per-document given by the Mover.
   * @param output the {@link SinkFn} to feed results to.
   * @param <T> the type of result expected from the query.
   */
  public static <T> void Evaluate(Mover mover, QueryEvalNode<T> query, SinkFn<DocumentResult<T>> output) {
    for(; mover.hasNext(); mover.isDone()) {
      int document = mover.currentKey();
      T value = query.calculate(document);
      if(value != null) {
        output.process(new DocumentResult<>(document, value));
      }
    }
  }

  public static <T> void EvaluateOneToMany(Mover mover, QueryEvalNode<List<T>> query, SinkFn<DocumentResult<T>> output) {
    for(; mover.hasNext(); mover.isDone()) {
      int document = mover.currentKey();
      List<T> values = query.calculate(document);
      if(values == null || values.isEmpty()) {
        continue;
      }
      for (T value : values) {
        output.process(new DocumentResult<>(document, value));
      }
    }

  }
}

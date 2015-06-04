package edu.umass.cs.jfoley.coop.querying.eval;

import ciir.jfoley.chai.fn.SinkFn;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;

import java.util.ArrayList;
import java.util.Collection;
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
    mover.execute(new DocumentQueryEvaluator<>(query, output));
  }

  public static <T> void EvaluateOneToMany(Mover mover, QueryEvalNode<? extends Collection<T>> query, SinkFn<DocumentResult<T>> output) {
    mover.execute(new DocumentToManyQueryEvaluator<>(query, output));
  }

  public static class DocumentQueryEvaluator<T> implements SinkFn<Integer> {
    private final QueryEvalNode<T> query;
    private final SinkFn<DocumentResult<T>> output;

    public DocumentQueryEvaluator(QueryEvalNode<T> query, SinkFn<DocumentResult<T>> output) {
      this.query = query;
      this.output = output;
    }

    @Override
    public void process(Integer document) {
      T value = query.calculate(document);
      if (value != null) {
        output.process(new DocumentResult<>(document, value));
      }
    }
  }
  public static class DocumentToManyQueryEvaluator<T> implements SinkFn<Integer> {
    private final QueryEvalNode<? extends Collection<T>> query;
    private final SinkFn<DocumentResult<T>> output;

    public DocumentToManyQueryEvaluator(QueryEvalNode<? extends Collection<T>> query, SinkFn<DocumentResult<T>> output) {
      this.query = query;
      this.output = output;
    }

    @Override
    public void process(Integer document) {
      Collection<T> values = query.calculate(document);
      if (values == null || values.isEmpty()) {
        return;
      }
      for (T value : values) {
        output.process(new DocumentResult<>(document, value));
      }
    }
  }
}

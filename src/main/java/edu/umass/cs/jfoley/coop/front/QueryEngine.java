package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.fn.SinkFn;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.AnyOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.io.postings.ArrayPosList;
import edu.umass.cs.ciir.waltz.phrase.OrderedWindow;
import edu.umass.cs.ciir.waltz.phrase.UnorderedWindow;
import edu.umass.cs.ciir.waltz.postings.extents.SpanIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.KeyMetadata;
import edu.umass.cs.ciir.waltz.sys.counts.CountMetadata;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class QueryEngine {
  public static class QueryRepr {
    public IntList uniqueTerms;

    public QueryRepr() {
      uniqueTerms = new IntList();
    }
    public int registerTerm(int term_i) {
      int index = uniqueTerms.indexOf(term_i);
      if(index < 0) {
        index = uniqueTerms.size();
        uniqueTerms.push(term_i);
      }
      return index;
    }

    public IntList getUniqueTerms() {
      return uniqueTerms;
    }

    public ArrayList<PostingMover<PositionsList>> getMovers(TermPositionsIndex index) throws IOException {
      ArrayList<PostingMover<PositionsList>> iters = new ArrayList<>();
      for (int uniqueTerm : getUniqueTerms()) {
        PostingMover<PositionsList> iter = index.getPositionsMover(uniqueTerm);
        iters.add(iter);
      }
      return iters;
    }
  }

  public static class PhraseNode {
    IntList termIdMapping = new IntList();
    ArrayList<SpanIterator> posIters;
    public PhraseNode(IntList ids, QueryRepr qctx) {
      posIters = new ArrayList<>();
      for (int id : ids) {
        termIdMapping.add(qctx.registerTerm(id));
        posIters.add(null);
      }
    }

    public void process(int doc, ArrayList<PostingMover<PositionsList>> iters, SinkFn<edu.umass.cs.jfoley.coop.querying.eval.DocumentResult<Integer>> output) {
      for (int i = 0; i < termIdMapping.size(); i++) {
        int trueTerm = termIdMapping.getQuick(i);
        PostingMover<PositionsList> mover = iters.get(trueTerm);
        PositionsList pl = mover.getPosting(doc);
        posIters.set(i, pl.getSpanIterator());
      }
      for (int position : OrderedWindow.findIter(posIters, 1)) {
        output.process(new edu.umass.cs.jfoley.coop.querying.eval.DocumentResult<>(doc, position));
      }
    }
  }

  interface MoverNode {
    Collection<Mover> getChildMovers();
  }
  public static class IndexedPositionsNode implements QCNode<PositionsList>, MoverNode {
    public final PostingMover<PositionsList> iter;
    private final int cf;

    public IndexedPositionsNode(PostingMover<PositionsList> iter) {
      this.iter = iter;
      KeyMetadata<?> m = iter.getMetadata();
      assert(m instanceof PositionsCountMetadata);
      this.cf = ((PositionsCountMetadata) m).totalCount;
    }

    @Override
    public Class<PositionsList> getResultClass() {
      return PositionsList.class;
    }

    @Override
    public ChildMovingLogic getMovingLogic() {
      return ChildMovingLogic.NA;
    }

    @Override
    public List<? extends QCNode<?>> children() { return Collections.emptyList(); }

    @Nullable
    @Override
    public PositionsList calculate(QueryEvaluationContext ctx, int document) {
      if(iter.matches(document)) {
        return iter.getPosting(document);
      }
      return null;
    }

    @Override
    public int getCollectionFrequency() { return cf; }

    @Override
    public List<Mover> getChildMovers() {
      return Collections.singletonList(iter);
    }
  }

  public static class IndexedCountsNode implements QCNode<Integer>, MoverNode {
    final PostingMover<Integer> countsMover;
    private final int cf;

    public IndexedCountsNode(PostingMover<Integer> countsMover) {
      this.countsMover = countsMover;
      assert countsMover.getMetadata() != null;
      this.cf = ((CountMetadata) countsMover.getMetadata()).totalCount;
    }

    @Override
    public Collection<Mover> getChildMovers() {
      return Collections.singletonList(countsMover);
    }

    @Override
    public Class<Integer> getResultClass() { return Integer.class; }

    @Override
    public ChildMovingLogic getMovingLogic() {
      return ChildMovingLogic.NA;
    }

    @Override
    public Collection<? extends QCNode<?>> children() {
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public Integer calculate(QueryEvaluationContext ctx, int document) {
      return count(ctx, document);
    }

    @Override
    public int count(QueryEvaluationContext ctx, int document) {
      if(countsMover.matches(document)) {
        return countsMover.getPosting(document);
      }
      return 0;
    }

    @Override
    public int getCollectionFrequency() {
      return cf;
    }
  }

  public static class AbstractPhraseNode extends QCApplyManyNode<PositionsList,PositionsList> {
    public AbstractPhraseNode(List<QCNode<PositionsList>> children) {
      super(ChildMovingLogic.AND, children);
    }

    @Override
    public Class<PositionsList> getResultClass() {
      return PositionsList.class;
    }

    @Nullable
    @Override
    public PositionsList calculate(QueryEvaluationContext ctx, int document) {
      ArrayList<SpanIterator> posIters = new ArrayList<>(children.size());
      for (QCNode<PositionsList> iter : children) {
        PositionsList pl = iter.calculate(ctx, document);
        if(pl == null) return null;
        posIters.add(pl.getSpanIterator());
      }

      return new ArrayPosList(OrderedWindow.findIter(posIters, 1));
    }
  }

  public static class CountPNode extends QCApplySingleNode<Integer, PositionsList> {
    public CountPNode(@Nonnull QCNode<PositionsList> child) {
      super(child);
    }

    @Override
    public Class<Integer> getResultClass() {
      return Integer.class;
    }

    @Nullable
    @Override
    public Integer calculate(QueryEvaluationContext ctx, int document) {
      PositionsList pl = child.calculate(ctx, document);
      return pl != null ? pl.size() : 0;
    }
  }

  public static abstract class QCApplyManyNode<Output, Input> implements QCNode<Output> {
    protected final ChildMovingLogic movingLogic;
    protected final List<QCNode<Input>> children;
    public QCApplyManyNode(ChildMovingLogic movingLogic, List<QCNode<Input>> children) {
      this.movingLogic = movingLogic;
      this.children = children;
    }
    @Override
    public boolean hasChildren() {
      return true;
    }
    @Override
    public ChildMovingLogic getMovingLogic() {
      return movingLogic;
    }
    @Override
    public List<QCNode<Input>> children() {
      return children;
    }
  }

  public static abstract class QCApplySingleNode<Output, Input> implements QCNode<Output> {
    protected final ChildMovingLogic movingLogic;
    protected final QCNode<Input> child;
    public QCApplySingleNode(@Nonnull QCNode<Input> child) {
      this.movingLogic = ChildMovingLogic.NA;
      this.child = Objects.requireNonNull(child);
    }
    @Override
    public ChildMovingLogic getMovingLogic() {
      return movingLogic;
    }
    @Override
    public List<QCNode<Input>> children() {
      return Collections.singletonList(child);
    }
    @Override
    public boolean hasChildren() {
      return true;
    }
    @Override public int getCollectionFrequency() { return child.getCollectionFrequency(); }
  }
  public static class AbstractSynonymNode extends QCApplyManyNode<PositionsList, PositionsList> {
    public AbstractSynonymNode(List<QCNode<PositionsList>> children) {
      super(ChildMovingLogic.OR, children);
    }

    @Override
    public Class<PositionsList> getResultClass() {
      return PositionsList.class;
    }

    @Nullable
    @Override
    public PositionsList calculate(QueryEvaluationContext ctx, int document) {
      ArrayList<SpanIterator> posIters = new ArrayList<>(children.size());
      for (QCNode<PositionsList> iter : children) {
        PositionsList pl = iter.calculate(ctx, document);
        if(pl == null) continue;
        posIters.add(pl.getSpanIterator());
      }
      if(posIters.isEmpty()) return null;
      ArrayPosList hits = new ArrayPosList(OrderedWindow.findOr(posIters));
      if(hits.isEmpty()) return null;
      return hits;
    }

  }

  public static class BigramCountNode extends QCApplyManyNode<Integer, PositionsList> implements CountableNode {
    public BigramCountNode(List<QCNode<PositionsList>> children) {
      super(ChildMovingLogic.AND, children);
    }

    @Nullable
    @Override
    public Integer calculate(QueryEvaluationContext ctx, int document) {
      ArrayList<SpanIterator> posIters = new ArrayList<>(children.size());
      for (QCNode<PositionsList> iter : children) {
        PositionsList pl = iter.calculate(ctx, document);
        if(pl == null) return null;
        posIters.add(pl.getSpanIterator());
      }
      return OrderedWindow.countIter(posIters, 1);
    }

    @Override
    public int getCollectionFrequency() {
      throw new UnsupportedOperationException();
    }
  }

  public interface CountableNode extends QCNode<Integer> {
    @Override
    default Class<Integer> getResultClass() {
      return Integer.class;
    }
  }

  public static class CombineNode extends QCApplyManyNode<Double, Double> {
    private final int NC;
    final double[] weights;
    public CombineNode(List<QCNode<Double>> children) {
      this(children, ListFns.fill(children.size(), ignored -> 1.0), true);
    }
    public CombineNode(List<QCNode<Double>> children, List<Double> weights, boolean norm) {
      super(ChildMovingLogic.AND, children);
      this.NC = children.size();
      assert(weights.size() == NC);
      this.weights = new double[NC];
      double sum = 0;
      for (int i = 0; i < NC; i++) {
        double w = weights.get(i);
        this.weights[i] = w;
        sum += w;
      }
      if(norm) {
        for (int i = 0; i < NC; i++) {
          this.weights[i] /= sum;
        }
      }
    }

    @Override
    public Class<Double> getResultClass() {
      return Double.class;
    }

    @Nullable
    @Override
    public Double calculate(QueryEvaluationContext ctx, int document) {
      return score(ctx, document);
    }

    @Override
    public final double score(QueryEvaluationContext ctx, int document) {
      double sum = 0;
      for (int i = 0; i < NC; i++) {
        double score = children.get(i).score(ctx, document);
        sum += weights[i] * score;
      }
      return sum;
    }
  }

  /** These guys make movement OR rather than AND, since they sanely reply to everything... */
  public static class LinearSmoothingNode extends QCApplySingleNode<Double, Integer> {
    private final double lambda;
    private double bgScore;

    public LinearSmoothingNode(@Nonnull QCNode<Integer> child) {
      this(child, 0.8);
    }
    public LinearSmoothingNode(@Nonnull QCNode<Integer> child, double lambda) {
      super(child);
      this.lambda = lambda;
      this.bgScore = Double.NaN;
    }

    @Override
    public ChildMovingLogic getMovingLogic() {
      return ChildMovingLogic.OR;
    }

    @Override
    public Class<Double> getResultClass() {
      return Double.class;
    }

    @Override
    public void setup(QueryEvaluationContext ctx) {
      double clen = ctx.getCollectionLength();
      int cf = child.getCollectionFrequency();
      this.bgScore = (1.0-lambda) * (cf / clen);
    }

    @Nullable
    @Override
    public final Double calculate(QueryEvaluationContext ctx, int document) {
      return score(ctx, document);
    }

    @Override
    public final double score(QueryEvaluationContext ctx, int document) {
      double len = ctx.getLength(document);
      int count = child.count(ctx, document);

      return Math.log(bgScore + lambda * (count / len));
    }
  }

  public interface QueryEvaluationContext {
    int getLength(int document);
    double getCollectionLength();

    QCNode<Integer> getUnigram(int lhs) throws IOException;
    QCNode<Integer> getBigram(int lhs, int rhs) throws IOException;
    QCNode<Integer> getUBigram(int lhs, int rhs) throws IOException;
  }

  public enum ChildMovingLogic { AND, OR, NA}
  public interface QCNode<T> {
    Class<T> getResultClass();

    /**
     * @return how this node expects to move; does it want all of its children (AND) or some of its children? (NONE). If this is indexed, go ahead and use NA instead of a policy.
     */
    ChildMovingLogic getMovingLogic();
    Collection<? extends QCNode<?>> children();
    @Nullable T calculate(QueryEvaluationContext ctx, int document);

    /** @return true if this node has children; false if otherwise. */
    default boolean hasChildren() {
      return !children().isEmpty();
    }
    /**
     * Can only use the faster AND movement if all operators are AND;
     * otherwise we may need to compute partial trees before we determine if the expression is null.
     * @return AND, OR or NA
     */
    default ChildMovingLogic calculateMovingLogic() {
      ChildMovingLogic mine = getMovingLogic();
      //System.err.println("calculateMovingLogic: "+mine+" "+this.toReprString());
      if(mine == ChildMovingLogic.AND) {
        assert(hasChildren());
        for (QCNode<?> qcNode : children()) {
          if (qcNode.calculateMovingLogic() == ChildMovingLogic.OR) {
            return ChildMovingLogic.OR;
          }
        }
        return ChildMovingLogic.AND;
      }
      return mine;
    }

    default CharSequence repr() {
      StringBuilder sb = new StringBuilder();
      sb.append("(").append(this.getClass().getSimpleName())
          .append(":").append(this.getResultClass().getSimpleName());
      if(this.hasChildren()) {
        for (QCNode<?> qcNode : children()) {
          sb.append(" ").append(qcNode.repr());
        }
      }
      sb.append(")");
      return sb;
    }

    default String toReprString() {
      return repr().toString();
    }


    /**
     * This call is only valid if this is a QCNode&lt;Integer&gt;
     */
    default int count(QueryEvaluationContext ctx, int document) {
      assert(getResultClass() == Integer.class);
      Integer ct = (Integer) calculate(ctx, document);
      if(ct == null) return 0;
      return ct;
    }
    /**
     * This call is only valid if this is a QCNode&lt;Double&gt;
     */
    default double score(QueryEvaluationContext ctx, int document) {
      assert(getResultClass() == Double.class);
      return Objects.requireNonNull((Double) calculate(ctx, document));
    }


    /**
     * If it is known, return the collection frequency of this node.
     * @return collection frequency; number of times it occurs in a collection.
     */
    default int getCollectionFrequency() { return -1; }

    /**
     * By default, propagate down the tree.
     * @param ctx
     */
    default void setup(QueryEvaluationContext ctx) {
      for (QCNode<?> qcNode : children()) { qcNode.setup(ctx); }
    }
  }

  @Nonnull
  public static List<Mover> findChildMovers(@Nonnull QCNode<?> expr) {
    HashSet<Mover> items = new HashSet<>();
    findChildMoversRecursively(expr, items);
    return new ArrayList<>(items);
  }

  @Nonnull
  public static Mover createMover(@Nonnull QCNode<?> expr) {
    ChildMovingLogic childMovingLogic = expr.calculateMovingLogic();
    List<Mover> m = findChildMovers(expr);
    if(childMovingLogic == ChildMovingLogic.NA) {
      if (m.size() != 1)
        throw new IllegalStateException("Calculated NA movement but have more than one child mover in expr: " + expr);
      assert (m.size() == 1);
      return m.get(0);
    } else if(m.size() == 1) {
      return m.get(0);
    } else if(childMovingLogic == ChildMovingLogic.AND) {
      return new AllOfMover<>(m);
    } else if(childMovingLogic == ChildMovingLogic.OR) {
      return new AnyOfMover<>(m);
    } else throw new UnsupportedOperationException("Bad ChildMovingLogic: "+childMovingLogic);
  }

  private static void findChildMoversRecursively(QCNode<?> expr, HashSet<Mover> items) {
    if(expr instanceof MoverNode) {
      items.addAll(((MoverNode) expr).getChildMovers());
    }
    for (QCNode<?> qcNode : expr.children()) {
      findChildMoversRecursively(qcNode, items);
    }
  }

  public static class UnorderedWindowNode extends QCApplyManyNode<PositionsList, PositionsList> implements QCNode<PositionsList> {
    public UnorderedWindowNode(List<QCNode<PositionsList>> children, int i) {
      super(ChildMovingLogic.AND, children);
    }

    @Override public Class<PositionsList> getResultClass() { return PositionsList.class; }

    @Nullable
    @Override
    public PositionsList calculate(QueryEvaluationContext ctx, int document) {
      ArrayList<SpanIterator> posIters = new ArrayList<>(children.size());
      for (QCNode<PositionsList> iter : children) {
        PositionsList pl = iter.calculate(ctx, document);
        if(pl == null) return null;
        posIters.add(pl.getSpanIterator());
      }
      return new ArrayPosList(UnorderedWindow.findIter(posIters, 1));
    }
  }
}

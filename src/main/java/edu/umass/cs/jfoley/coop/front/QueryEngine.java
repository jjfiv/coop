package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.fn.SinkFn;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.AnyOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.io.postings.ArrayPosList;
import edu.umass.cs.ciir.waltz.phrase.OrderedWindow;
import edu.umass.cs.ciir.waltz.postings.extents.SpanIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;

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
    public IndexedPositionsNode(PostingMover<PositionsList> iter) {
      this.iter = iter;
    }

    @Override
    public ChildMovingLogic getMovingLogic() {
      return ChildMovingLogic.NO_CHILDREN;
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
    public List<Mover> getChildMovers() {
      return Collections.singletonList(iter);
    }
  }

  public static class AbstractPhraseNode extends QCApplyManyNode<PositionsList,PositionsList> {
    public AbstractPhraseNode(List<QCNode<PositionsList>> children) {
      super(ChildMovingLogic.AND, children);
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

  public static abstract class QCApplyManyNode<Output, Input> implements QCNode<Output> {
    protected final ChildMovingLogic movingLogic;
    protected final List<QCNode<Input>> children;
    public QCApplyManyNode(ChildMovingLogic movingLogic, List<QCNode<Input>> children) {
      this.movingLogic = movingLogic;
      this.children = children;
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

  public static class AbstractSynonymNode extends QCApplyManyNode<PositionsList, PositionsList> {
    public AbstractSynonymNode(List<QCNode<PositionsList>> children) {
      super(ChildMovingLogic.OR, children);
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

  public static class BigramCountNode extends QCApplyManyNode<Integer, PositionsList> {
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
  }

  public interface QueryEvaluationContext {
    int getLength(int document);
  }

  public enum ChildMovingLogic { AND, OR, NO_CHILDREN }
  public interface QCNode<T> {
    /**
     * @return how this node expects to move; does it want all of its children (AND) or some of its children? (NONE). If this is indexed, go ahead and use NO_CHILDREN instead of a policy.
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
     * @return AND, OR or NO_CHILDREN
     */
    default ChildMovingLogic calculateMovingLogic() {
      ChildMovingLogic mine = getMovingLogic();
      if(mine == ChildMovingLogic.OR) {
        assert(hasChildren());
        for (QCNode<?> qcNode : children()) {
          if (qcNode.calculateMovingLogic() == ChildMovingLogic.AND) {
            return ChildMovingLogic.AND;
          }
        }
        return ChildMovingLogic.OR;
      }
      return mine;
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
    if(childMovingLogic == ChildMovingLogic.NO_CHILDREN) {
      if(m.size() != 1) throw new IllegalStateException("Calculated NO_CHILDREN movement but have more than one child mover in expr: "+expr);
      assert(m.size() == 1);
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

}

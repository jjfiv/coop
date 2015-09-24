package edu.umass.cs.jfoley.coop.front;

import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author jfoley
 */
public class QueryEngineTest {

  public static class FakePositionsNode implements QueryEngine.QCNode<PositionsList> {
    @Override public QueryEngine.ChildMovingLogic getMovingLogic() { return QueryEngine.ChildMovingLogic.NO_CHILDREN; }
    @Override public Collection<? extends QueryEngine.QCNode<?>> children() { return Collections.emptyList(); }
    @Nullable
    @Override
    public PositionsList calculate(QueryEngine.QueryEvaluationContext ctx, int document) { throw new UnsupportedOperationException(); }
  }

  @Test
  public void testCalculateMovingLogic() {
    FakePositionsNode a = new FakePositionsNode();
    FakePositionsNode b = new FakePositionsNode();
    FakePositionsNode c = new FakePositionsNode();

    QueryEngine.AbstractPhraseNode ab = new QueryEngine.AbstractPhraseNode(Arrays.asList(a,b));
    QueryEngine.AbstractPhraseNode cab = new QueryEngine.AbstractPhraseNode(Arrays.asList(ab,c));
    QueryEngine.AbstractPhraseNode aab = new QueryEngine.AbstractPhraseNode(Arrays.asList(ab,a));

    QueryEngine.QCNode<?> cORab = new QueryEngine.AbstractSynonymNode(Arrays.asList(c, ab));

    assert(ab.calculateMovingLogic() == QueryEngine.ChildMovingLogic.AND);
    assert(cab.calculateMovingLogic() == QueryEngine.ChildMovingLogic.AND);
    assert(aab.calculateMovingLogic() == QueryEngine.ChildMovingLogic.AND);
    assert(cORab.getMovingLogic() == QueryEngine.ChildMovingLogic.OR);
    assert(cORab.calculateMovingLogic() == QueryEngine.ChildMovingLogic.AND);
  }
}
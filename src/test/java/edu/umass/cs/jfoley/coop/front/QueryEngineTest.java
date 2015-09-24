package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.ciir.waltz.compat.galago.iters.GalagoCountIterator;
import edu.umass.cs.ciir.waltz.dociter.ListBlockPostingsIterator;
import edu.umass.cs.ciir.waltz.dociter.movement.BlockPostingsMover;
import edu.umass.cs.ciir.waltz.dociter.movement.IdSetMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.SimplePosting;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import org.junit.Test;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.iterator.ScoreCombinationIterator;
import org.lemurproject.galago.core.retrieval.iterator.ScoreIterator;
import org.lemurproject.galago.core.retrieval.iterator.scoring.JelinekMercerScoringIterator;
import org.lemurproject.galago.core.retrieval.processing.ScoringContext;
import org.lemurproject.galago.core.retrieval.query.NodeParameters;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class QueryEngineTest {

  public static class FakePositionsNode implements QueryEngine.QCNode<PositionsList> {
    @Override public Class<PositionsList> getResultClass() { return PositionsList.class; }
    @Override public QueryEngine.ChildMovingLogic getMovingLogic() { return QueryEngine.ChildMovingLogic.NA; }
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
    assert(cORab.calculateMovingLogic() == QueryEngine.ChildMovingLogic.OR);
  }

  public static class FakeCountsNode implements QueryEngine.QCNode<Integer>, QueryEngine.MoverNode, QueryEngine.CountableNode {
    final List<Pair<Integer, Integer>> postings;
    int cf;

    public FakeCountsNode(List<Pair<Integer, Integer>> postings) {
      this.postings = postings;
      this.cf = 0;
      for (Pair<Integer, Integer> posting : postings) {
        cf += posting.right;
      }
    }

    @Override
    public QueryEngine.ChildMovingLogic getMovingLogic() {
      return QueryEngine.ChildMovingLogic.NA;
    }

    @Override
    public Collection<? extends QueryEngine.QCNode<?>> children() { return Collections.emptyList(); }

    @Nullable
    @Override
    public Integer calculate(QueryEngine.QueryEvaluationContext ctx, int document) {
      for (Pair<Integer, Integer> posting : postings) {
        if(posting.left == document) {
          return posting.right;
        }
      }
      return null;
    }

    @Override
    public Collection<Mover> getChildMovers() {
      return Collections.singletonList(new IdSetMover(ListFns.map(postings, Pair::getKey)));
    }

    @Override
    public int getCollectionFrequency() {
      return cf;
    }

    public PostingMover<Integer> asMover() {
      return new BlockPostingsMover<>(new ListBlockPostingsIterator<>(ListFns.lazyMap(postings, (pair) -> new SimplePosting<>(pair.left, pair.right))));
    }
  }

  @Test
  public void testQueryLikelihood() throws IOException {
    final int docLength = 15;
    final int collectionLength = 1000;

    // expect: 2,4,1,6,bgs
    List<Pair<Integer, Integer>> aPostings = Arrays.asList(Pair.of(1, 5), Pair.of(2, 1), Pair.of(4, 3));
    List<Pair<Integer, Integer>> bPostings = Arrays.asList(               Pair.of(2, 5), Pair.of(4, 1), Pair.of(6, 3));

    FakeCountsNode aCounts = new FakeCountsNode(aPostings);
    FakeCountsNode bCounts = new FakeCountsNode(bPostings);

    GalagoCountIterator iter = new GalagoCountIterator(aCounts.asMover());
    GalagoCountIterator lengths = new GalagoCountIterator(new BlockPostingsMover<>(new ListBlockPostingsIterator<>(
        ListFns.fill(10, (x) -> new SimplePosting<>(x, 15)))));

    NodeParameters jmParam = NodeParameters.create().set("lambda", 0.8).set("collectionLength", collectionLength).set("maximumCount", 1);
    ScoreIterator galagoQL = new ScoreCombinationIterator(
        NodeParameters.create().set("norm", true),
        new ScoreIterator[] {
            new JelinekMercerScoringIterator(
                jmParam.clone().set("nodeFrequency", aCounts.getCollectionFrequency()),
                lengths,
                new GalagoCountIterator(aCounts.asMover())
            ),
            new JelinekMercerScoringIterator(
                jmParam.clone().set("nodeFrequency", bCounts.getCollectionFrequency()),
                lengths,
                new GalagoCountIterator(bCounts.asMover())
            )
        }
    );

    Function<Integer, Double> scoreWithGalago = (docId) -> {
      ScoringContext ctx = new ScoringContext(docId);
      try {
        galagoQL.reset();
        galagoQL.syncTo(docId);
        return galagoQL.score(ctx);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };

    QueryEngine.QCNode<Double> ql = new QueryEngine.CombineNode(Arrays.asList(
        new QueryEngine.LinearSmoothingNode(aCounts),
        new QueryEngine.LinearSmoothingNode(bCounts)));

    assertEquals(QueryEngine.ChildMovingLogic.OR, ql.calculateMovingLogic());

    QueryEngine.QueryEvaluationContext fakeIndex = new QueryEngine.QueryEvaluationContext() {
      @Override public int getLength(int document) { return docLength; }
      @Override public double getCollectionLength() { return collectionLength; }
    };
    double backgroundScore = ql.score(fakeIndex, 0);
    assertEquals(backgroundScore, ql.score(fakeIndex, 7), 0.00001);

    ArrayList<ScoredDocument> sdoc = new ArrayList<>();

    for (int i = 0; i < 7; i++) {
      double newScore = ql.score(fakeIndex, i);
      double gScore = scoreWithGalago.apply(i);
      //System.out.println("["+i+"] neo="+newScore+" galago="+gScore);
      assertEquals(gScore, newScore, 0.001);
      sdoc.add(new ScoredDocument(i, newScore));
    }
    Collections.sort(sdoc, new ScoredDocument.ScoredDocumentComparator().reversed());
    assertEquals(2, sdoc.get(0).document);
    assertEquals(4, sdoc.get(1).document);
    assertEquals(1, sdoc.get(2).document);
    assertEquals(6, sdoc.get(3).document);

    // background-scores
    assertEquals(0, sdoc.get(4).document);
    assertEquals(backgroundScore, sdoc.get(4).score, 0.0001);
    assertEquals(3, sdoc.get(5).document);
    assertEquals(backgroundScore, sdoc.get(5).score, 0.0001);
    assertEquals(5, sdoc.get(6).document);
    assertEquals(backgroundScore, sdoc.get(6).score, 0.0001);


    // Now try mover:
    TopKHeap<ScoredDocument> best = new TopKHeap<>(3);
    Mover mover = QueryEngine.createMover(ql);
    mover.execute((doc) -> {
      best.offer(new ScoredDocument(doc, ql.score(fakeIndex, doc)));
    });

    // OR-logic visits all 4 non-zero documents:
    assertEquals(4, best.getTotalSeen());

    List<ScoredDocument> results = new ArrayList<>(best.getSorted());
    assertEquals(3, results.size());
    assertEquals(2, results.get(0).document);
    assertEquals(4, results.get(1).document);
    assertEquals(1, results.get(2).document);

    assertEquals(ListFns.slice(sdoc, 0, 3), results);
  }
}
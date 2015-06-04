package edu.umass.cs.jfoley.coop.querying.eval;

import ciir.jfoley.chai.lang.Require;
import edu.umass.cs.ciir.waltz.feature.Feature;
import edu.umass.cs.ciir.waltz.phrase.OrderedWindow;
import edu.umass.cs.ciir.waltz.postings.extents.ExtentsIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.postings.positions.SimplePositionsList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class QueryEval {
  /**
   * Unlike Galago, our calculation is nullable, and generic.
   * Nullability gives us the ability to only calculate matches in one place.
   * Genericity gives us the ability to have our processing return anything if there's a node for it. Galago makes the assumption that the top level is a double, so if you can't squeeze it into a double you can't use it.
   * @param <T> the type of value to collect per document.
   * @see QueryEvalEngine
   * @see DocumentResult
   */
  interface QueryEvalNode<T> {
    @Nullable
    T calculate(int document);
  }
  interface CountNode extends QueryEvalNode<Integer> { }
  interface PositionsNode extends QueryEvalNode<PositionsList> { }
  public static abstract class MappingQueryNode<A,B> implements QueryEvalNode<B> {
    public final QueryEvalNode<A> inner;
    protected MappingQueryNode(QueryEvalNode<A> inner) {
      this.inner = inner;
    }
  }

  /**
   * A leaf query node built on Waltz's Feature abstraction. Note that they may be MoverFeatures, but they should never move from here.
   * @param <X> type of feature.
   * @see Feature
   */
  public static abstract class FeatureQueryNode<X> implements QueryEvalNode<X> {
    protected Feature<X> feature;
    protected FeatureQueryNode(Feature<X> feature) {
      this.feature = feature;
    }
    protected boolean hasFeature(int document) {
      return feature.hasFeature(document);
    }
    protected X getFeature(int document) {
      return feature.getFeature(document);
    }
  }

  public static class FeatureCountNode extends FeatureQueryNode<Integer> {
    protected FeatureCountNode(Feature<Integer> feature) {
      super(feature);
    }
    @Nullable
    public Integer calculate(int document) {
      return getFeature(document);
    }
  }

  public static class FeaturePositionsNode extends FeatureQueryNode<PositionsList> implements PositionsNode {
    protected FeaturePositionsNode(Feature<PositionsList> feature) {
      super(feature);
    }

    @Nullable
    @Override
    public PositionsList calculate(int document) {
      return getFeature(document);
    }
  }

  public static class CountPositionsNode extends MappingQueryNode<PositionsList,Integer> {
    protected CountPositionsNode(QueryEvalNode<PositionsList> inner) {
      super(inner);
    }

    @Nullable
    @Override
    public Integer calculate(int document) {
      PositionsList positions = inner.calculate(document);
      if(positions == null) return null;
      return positions.size();
    }
  }

  public static class PhraseNode implements QueryEvalNode<PositionsList> {
    private final List<QueryEvalNode<PositionsList>> children;

    protected PhraseNode(List<QueryEvalNode<PositionsList>> children) {
      Require.that(children.size() > 1);
      this.children = children;
    }

    @Nullable
    @Override
    public PositionsList calculate(int document) {
      List<ExtentsIterator> childLists = new ArrayList<>();
      for (QueryEvalNode<PositionsList> child : children) {
        PositionsList ofChild = child.calculate(document);
        if(ofChild == null || ofChild.size() == 0) return null;
        childLists.add(ofChild.getExtentsIterator());
      }
      return new SimplePositionsList(OrderedWindow.findIter(childLists, 1));
    }
  }

}

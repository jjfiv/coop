package edu.umass.cs.jfoley.coop.querying.eval;

import ciir.jfoley.chai.lang.Require;
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

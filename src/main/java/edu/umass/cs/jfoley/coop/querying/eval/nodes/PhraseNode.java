package edu.umass.cs.jfoley.coop.querying.eval.nodes;

import edu.umass.cs.ciir.waltz.phrase.OrderedWindow;
import edu.umass.cs.ciir.waltz.postings.extents.SpanIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.postings.positions.SimplePositionsList;
import edu.umass.cs.jfoley.coop.querying.eval.QueryEvalNode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class PhraseNode implements QueryEvalNode<PositionsList> {
  private final List<QueryEvalNode<PositionsList>> children;

  public PhraseNode(List<QueryEvalNode<PositionsList>> children) {
    assert(children.size() > 1);
    this.children = children;
  }

  @Nullable
  @Override
  public PositionsList calculate(int document) {
    List<SpanIterator> childLists = new ArrayList<>();
    for (QueryEvalNode<PositionsList> child : children) {
      PositionsList ofChild = child.calculate(document);
      if (ofChild == null || ofChild.size() == 0) return null;
      childLists.add(ofChild.getSpanIterator());
    }
    return new SimplePositionsList(OrderedWindow.findIter(childLists, 1));
  }
}

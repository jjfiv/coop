package edu.umass.cs.jfoley.coop.querying.eval.nodes;

import ciir.jfoley.chai.collections.list.BitVector;
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
  private final ArrayList<BitVector> vectors;
  int MaxLength = 4096;

  public PhraseNode(List<QueryEvalNode<PositionsList>> children) {
    assert(children.size() > 1);
    this.children = children;
    this.vectors = new ArrayList<>();
    for (int i = 0; i < children.size(); i++) {
      vectors.add(new BitVector(MaxLength)); // longest supported document
    }
  }

  public static final boolean useBitmap = false;
  @Nullable
  @Override
  public PositionsList calculate(int document) {
    List<SpanIterator> childLists = new ArrayList<>();
    for (int i = 0; i < children.size(); i++) {
      QueryEvalNode<PositionsList> child = children.get(i);
      PositionsList ofChild = child.calculate(document);
      if (ofChild == null || ofChild.size() == 0) return null;

      if(useBitmap) {
        BitVector v = vectors.get(i);
        v.clear();
        for (int posIndex = 0; posIndex < ofChild.size(); posIndex++) {
          int pos = ofChild.getPosition(posIndex);
          if (pos >= MaxLength) break;
          v.set(pos);
        }
        v.shiftLeft(i);
      } else {
        childLists.add(ofChild.getSpanIterator());
      }
    }
    if(!useBitmap) {
      return new SimplePositionsList(OrderedWindow.findIter(childLists, 1));
    } else {
      BitVector first = vectors.get(0);
      for (int i = 1; i < vectors.size(); i++) {
        first.and(vectors.get(i));
      }
      return new SimplePositionsList(first.extract());
    }
  }
}

package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.fn.SinkFn;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.phrase.OrderedWindow;
import edu.umass.cs.ciir.waltz.postings.extents.SpanIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;

import java.io.IOException;
import java.util.ArrayList;

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

    public ArrayList<PostingMover<PositionsList>> getMovers(CoopIndex index) throws IOException {
      ArrayList<PostingMover<PositionsList>> iters = new ArrayList<>();
      for (int uniqueTerm : getUniqueTerms()) {
        PostingMover<PositionsList> iter = index.getPositionsMover("lemmas", uniqueTerm);
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

    public void process(int doc, ArrayList<PostingMover<PositionsList>> iters, SinkFn<DocumentResult<Integer>> output) {
      for (int i = 0; i < termIdMapping.size(); i++) {
        int trueTerm = termIdMapping.getQuick(i);
        PostingMover<PositionsList> mover = iters.get(trueTerm);
        PositionsList pl = mover.getPosting(doc);
        posIters.set(i, pl.getSpanIterator());
      }
      for (int position : OrderedWindow.findIter(posIters, 1)) {
        output.process(new DocumentResult<>(doc, position));
      }
    }
  }


}

package edu.umass.cs.jfoley.coop.front.eval;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.phrase.UnorderedWindow;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.ciir.waltz.postings.extents.SpanIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class EvaluateBagOfWordsMethod extends FindHitsMethod {
  private final TermPositionsIndex index;
  private final IntList queryIds;
  private final int passageSize;

  public EvaluateBagOfWordsMethod(Parameters input, Parameters output, TermPositionsIndex index) throws IOException {
    super(input, output);
    this.index = index;
    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = ListFns.map(tokenizer.createDocument("tmp", input.getString("query")).getTerms(tokenizer.getDefaultTermSet()), String::toLowerCase);
    output.put("queryTerms", query);
    this.passageSize = input.get("passageSize", 32);
    this.queryIds = index.translateFromTerms(ListFns.unique(query));
    output.put("queryIds", queryIds);
  }

  @Override
  public ArrayList<DocumentResult<Integer>> compute() throws IOException {
    List<PostingMover<PositionsList>> movers = new ArrayList<>(queryIds.size());
    for (int term : queryIds) {
      movers.add(index.getPositionsMover(term));
    }

    //System.err.println(queryIds);
    //System.err.println(movers);

    ArrayList<DocumentResult<Integer>> output = new ArrayList<>();

    /*AnyOfMover<?> orMover = new AnyOfMover<>(movers);
    for(; !orMover.isDone(); orMover.next()) {
      int doc = orMover.currentKey();
      int length = index.getLength(doc);
      int windows = length / passageSize;


      HashSet<Integer> uniquePos = new HashSet<>();
      ArrayList<SpanIterator> posLists = new ArrayList<>();
      for (PostingMover<PositionsList> mover : movers) {
        if (mover.matches(doc)) {
          PositionsList pl = mover.getPosting(doc);
          uniquePos.addAll(pl);
          posLists.add(pl.getSpanIterator());
        }
      }


      for (int i = 0; i < length; i+=passageSize) {
        int limit = i+passageSize;
        for (SpanIterator iter : posLists) {
          int count = 0;
          for(; !iter.isDone(); iter.next()) {
            int begin = iter.currentBegin();
            if(begin < i) continue;
            if(begin >= limit) break;
            count++;
          }
        }
      }
      for (int pos : IterableFns.sorted(uniquePos)) {
        output.add(new DocumentResult<>(doc, pos));
      }
    };*/

    if(movers.size() == 1) {
      PostingMover<PositionsList> m = movers.get(0);
      for (m.start(); !m.isDone(); m.next()) {
        int doc = m.currentKey();
        PositionsList docHits = m.getPosting(doc);
        for (int i = 0; i < docHits.size(); i++) {
          output.add(new DocumentResult<>(doc, docHits.getPosition(i)));
        }
      }
    } else {
      AllOfMover<?> andMover = new AllOfMover<>(movers);
      for (; !andMover.isDone(); andMover.next()) {
        int doc = andMover.currentKey();
        ArrayList<SpanIterator> iters = new ArrayList<>();
        for (PostingMover<PositionsList> mover : movers) {
          iters.add(mover.getPosting(doc).getSpanIterator());
        }
        for (Span span : UnorderedWindow.calculateSpans(iters, passageSize)) {
          output.add(new DocumentResult<>(doc, span.begin));
        }
      }
    }

    return output;
  }

  @Override
  public int getPhraseWidth() {
    return passageSize;
  }

  @Override
  public boolean queryContains(int term) {
    return queryIds.containsInt(term);
  }
}

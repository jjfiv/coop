package edu.umass.cs.jfoley.coop.front.eval;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.AnyOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.phrase.UnorderedWindow;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.ciir.waltz.postings.extents.SpanIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.set.hash.TIntHashSet;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author jfoley
 */
public class EvaluateBagOfWordsMethod extends FindHitsMethod {
  private final TermPositionsIndex index;
  private final IntList queryIds;
  private final int passageSize;

  public static Set<String> stopwords;

  static {
    try {
      stopwords = WordLists.getWordList("inquery");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public EvaluateBagOfWordsMethod(Parameters input, Parameters output, TermPositionsIndex index) throws IOException {
    super(input, output);
    this.index = index;
    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = ListFns.map(tokenizer.createDocument("tmp", input.getString("query")).getTerms(tokenizer.getDefaultTermSet()), String::toLowerCase);

    // use stopword-free version if possible.
    List<String> nonStopword = new ArrayList<>();
    for (String s : query) {
      if(!stopwords.contains(s)) {
        nonStopword.add(s);
      }
    }
    if(!nonStopword.isEmpty()) {
      query = nonStopword;
    }

    output.put("queryTerms", query);
    this.passageSize = input.get("passageSize", 32);
    this.queryIds = index.translateFromTerms(ListFns.unique(query));
    output.put("queryIds", queryIds);


  }

  @Override
  public ArrayList<DocumentResult<Integer>> compute() throws IOException {
    List<PostingMover<PositionsList>> movers = new ArrayList<>(queryIds.size());
    for (int term : queryIds) {
      PostingMover<PositionsList> m = index.getPositionsMover(term);
      if(m != null) movers.add(m);
    }

    ArrayList<DocumentResult<Integer>> output = new ArrayList<>();

    if(movers.size() == 0) return output;
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
      // Try hard-computation:
      AllOfMover<?> mAND = new AllOfMover<>(movers);
      for (mAND.start(); !mAND.isDone(); mAND.next()) {
        int doc = mAND.currentKey();
        ArrayList<SpanIterator> iters = new ArrayList<>();
        for (PostingMover<PositionsList> mover : movers) {
          iters.add(mover.getPosting(doc).getSpanIterator());
        }
        for (Span span : UnorderedWindow.calculateSpans(iters, passageSize)) {
          output.add(new DocumentResult<>(doc, span.begin));
        }
      }

      // fall-back to soft-computation
      if(output.isEmpty()) {
        System.err.println("# AND returned no results, fall back to OR.");
        // reset!
        for (PostingMover<PositionsList> mover : movers) {
          mover.reset();
        }

        AnyOfMover<?> mOR = new AnyOfMover<>(movers);
        for (mOR.start(); !mOR.isDone(); mOR.next()) {
          int doc = mOR.currentKey();
          ArrayList<SpanIterator> iters = new ArrayList<>();
          for (PostingMover<PositionsList> mover : movers) {
            if(mover.matches(doc)) {
              iters.add(mover.getPosting(doc).getSpanIterator());
            }
          }
          for (int begin : calculateAnySpans(iters)) {
            output.add(new DocumentResult<>(doc, begin));
          }
        }
      }

    }

    return output;
  }

  public static IntList calculateAnySpans(List<SpanIterator> iters) {
    TIntHashSet hits = new TIntHashSet();
    for (SpanIterator iter : iters) {
      while(!iter.isDone()) {
        hits.add(iter.currentBegin());
        iter.next();
      }
    }
    IntList out = new IntList(hits.toArray());
    out.sort();
    return out;
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

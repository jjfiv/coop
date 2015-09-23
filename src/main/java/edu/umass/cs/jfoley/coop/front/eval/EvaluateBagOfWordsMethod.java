package edu.umass.cs.jfoley.coop.front.eval;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.ciir.waltz.dociter.movement.AnyOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author jfoley
 */
public class EvaluateBagOfWordsMethod extends FindHitsMethod {
  private final TermPositionsIndex index;
  private final IntList queryIds;

  public EvaluateBagOfWordsMethod(Parameters input, Parameters output, TermPositionsIndex index) throws IOException {
    super(input, output);
    this.index = index;
    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = ListFns.map(tokenizer.createDocument("tmp", input.getString("query")).getTerms(tokenizer.getDefaultTermSet()), String::toLowerCase);
    output.put("queryTerms", query);
    this.queryIds = index.translateFromTerms(ListFns.unique(query));
    output.put("queryIds", queryIds);
  }

  @Override
  public ArrayList<DocumentResult<Integer>> compute() throws IOException {
    List<PostingMover<PositionsList>> movers = new ArrayList<>(queryIds.size());
    for (int term : queryIds) {
      movers.add(index.getPositionsMover(term));
    }

    System.err.println(queryIds);
    System.err.println(movers);

    ArrayList<DocumentResult<Integer>> output = new ArrayList<>();

    AnyOfMover<?> orMover = new AnyOfMover<>(movers);
    for(; !orMover.isDone(); orMover.next()) {
      int doc = orMover.currentKey();
      HashSet<Integer> uniquePos = new HashSet<>();
      for (PostingMover<PositionsList> mover : movers) {
        if (mover.matches(doc)) {
          uniquePos.addAll(mover.getPosting(doc));
        }
      }
      for (int pos : IterableFns.sorted(uniquePos)) {
        output.add(new DocumentResult<>(doc, pos));
      }
    };

    return output;
  }

  @Override
  public int getPhraseWidth() {
    return 1;
  }

  @Override
  public boolean queryContains(int term) {
    return queryIds.containsInt(term);
  }
}

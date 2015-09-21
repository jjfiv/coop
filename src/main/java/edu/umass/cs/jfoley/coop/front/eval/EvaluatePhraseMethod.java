package edu.umass.cs.jfoley.coop.front.eval;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
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
public class EvaluatePhraseMethod extends FindHitsMethod {
  private final TermPositionsIndex index;
  private final IntList queryIds;

  public EvaluatePhraseMethod(Parameters input, Parameters output, TermPositionsIndex index) throws IOException {
    super(input, output);
    this.index = index;
    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = ListFns.map(tokenizer.createDocument("tmp", input.getString("query")).getTerms(tokenizer.getDefaultTermSet()), String::toLowerCase);
    output.put("queryTerms", query);
    this.queryIds = index.translateFromTerms(query);
    output.put("queryIds", queryIds);
  }

  @Override
  public ArrayList<DocumentResult<Integer>> compute() throws IOException {
    return index.locatePhrase(queryIds);
  }

  @Override
  public int getPhraseWidth() {
    return queryIds.size();
  }

  @Override
  public boolean queryContains(int term) {
    return queryIds.containsInt(term);
  }
}

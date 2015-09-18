package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsReader;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.set.hash.TIntHashSet;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * @author jfoley
 */
public class SuggestEntityFn extends CoopIndexServerFn {
  private final PhraseHitsReader phrases;

  public SuggestEntityFn(PhraseHitsReader phrases) {
    super(phrases.getIndex());
    this.phrases = phrases;
  }

  @Override
  public Parameters handleRequest(Parameters p) throws IOException, SQLException {
    final Parameters output = Parameters.create();
    final String termKind = p.get("termKind", "lemmas");

    TermPositionsIndex target = phrases.getPhrasesByTerm();

    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = ListFns.map(tokenizer.createDocument("tmp", p.getString("query")).getTerms(termKind), String::toLowerCase);
    output.put("queryTerms", query);

    IntList queryIds = index.translateFromTerms(query);
    output.put("queryIds", queryIds);

    if(!queryIds.containsInt(-1)) {
      List<DocumentResult<Integer>> results = target.locatePhrase(queryIds);
      System.err.println("# found "+results.size()+" phrase matches.");
      TIntHashSet docs = new TIntHashSet();
      for (DocumentResult<Integer> result : results) {
        docs.add(result.document);
      }
      System.err.println("# found "+results.size()+" phrase matches in "+docs.size()+" documents.");
      output.put("matchingDocs", new IntList(docs.toArray()));
    }

    return output;
  }
}

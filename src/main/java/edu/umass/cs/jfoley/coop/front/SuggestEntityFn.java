package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsReader;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.set.hash.TIntHashSet;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
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
    final boolean pullMetadata = p.get("meta", false);

    TermPositionsIndex target = phrases.getPhrasesByTerm();

    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = ListFns.map(tokenizer.createDocument("tmp", p.getString("query")).getTerms(termKind), String::toLowerCase);
    output.put("queryTerms", query);

    IntList queryIds = index.translateFromTerms(query);
    output.put("queryIds", queryIds);

    IntList matchingPhraseIds = new IntList();
    if(!queryIds.containsInt(-1)) {
      List<DocumentResult<Integer>> results = target.locatePhrase(queryIds);
      System.err.println("# found "+results.size()+" phrase matches.");
      TIntHashSet docs = new TIntHashSet();
      for (DocumentResult<Integer> result : results) {
        docs.add(result.document);
      }
      matchingPhraseIds = new IntList(docs.toArray());
      System.err.println("# found " + results.size() + " phrase matches in " + docs.size() + " documents.");
    }
    matchingPhraseIds.sort();

    ArrayList<Parameters> matchInfo = new ArrayList<>();
    for (Pair<Integer, IntList> kv : phrases.getPhraseVocab().getForward(matchingPhraseIds)) {
      int phraseId = kv.getKey();
      Parameters info = Parameters.create();
      info.put("id", phraseId);
      info.put("ids", kv.getValue());
      info.put("terms", index.translateToTerms(kv.getValue()));
      if(pullMetadata) {
        PositionsCountMetadata meta = phrases.getPhraseMetadata(phraseId);
        info.put("meta", Parameters.wrap(meta.toMap()));
      }
      matchInfo.add(info);
    }
    output.put("matches", matchInfo);

    return output;
  }
}

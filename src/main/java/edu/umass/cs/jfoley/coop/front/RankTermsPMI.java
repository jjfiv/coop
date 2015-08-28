package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.querying.LocatePhrase;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class RankTermsPMI extends CoopIndexServerFn {
  protected RankTermsPMI(IndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters p) throws IOException, SQLException {
    Parameters output = Parameters.create();
    int leftWidth = p.get("leftWidth", 5);
    assert(leftWidth >= 0);
    int rightWidth = p.get("rightWidth", 5);
    assert(rightWidth >= 0);
    int limit = p.get("limit", 20);
    assert(limit > 0);
    int hitLimit = p.get("hitLimit", Integer.MAX_VALUE);
    assert(hitLimit > 0);

    String termKind = p.get("termKind", "lemmas");

    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = tokenizer.createDocument("tmp", p.getString("query")).getTerms(termKind);

    output.put("queryTerms", query);


    Pair<Long, List<DocumentResult<Integer>>> hits = Timing.milliseconds(() -> LocatePhrase.find(index, termKind, query));
    int queryFrequency = hits.right.size();

    output.put("queryFrequency", hits.right.size());
    output.put("queryTime", hits.left);

    // build slices from the results, based on arguments to this file:
    List<TermSlice> slices = new ArrayList<>();
    for (DocumentResult<Integer> hit : ListFns.take(hits.right, hitLimit)) {
      slices.add(new TermSlice(
          hit.document,
          hit.value - leftWidth,
          hit.value + query.size() + rightWidth));
    }

    // Now score the nearby terms!
    final TObjectIntHashMap<String> termProxCounts = new TObjectIntHashMap<>();

    long candidateFindingTime = Timing.milliseconds(() ->
        index.getCorpus().forTermInSlice(slices, (term) ->
            termProxCounts.adjustOrPutValue(term, 1, 1)));

    output.put("candidateFindingTime", candidateFindingTime);

    // Okay, now actually score these candidates!
    double collectionLength = index.getCollectionLength();
    TopKHeap<PMITerm> topTerms = new TopKHeap<>(limit, Comparing.defaultComparator());

    long scoringTime = Timing.milliseconds(() -> {
      // Now lookup collection frequencies, this is p_x to termProxCounts p_xy
      termProxCounts.forEachEntry((term, frequency) -> {
        // skip query itself.
        if(!query.contains(term)) {
          topTerms.add(new PMITerm(
              term,
              index.collectionFrequency(term),
              queryFrequency,
              frequency,
              collectionLength));
        }
        return true;
      });
    });
    output.put("scoringTime", scoringTime);
    output.put("scoreCount", termProxCounts.size());

    List<Parameters> terms = new ArrayList<>();
    for (PMITerm pmiTerm : topTerms.getSorted()) {
      terms.add(pmiTerm.toJSON());
    }
    output.put("topTerms", terms);
    return output;
  }
}

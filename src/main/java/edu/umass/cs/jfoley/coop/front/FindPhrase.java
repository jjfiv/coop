package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.querying.LocatePhrase;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class FindPhrase extends CoopIndexServerFn {
  protected FindPhrase(CoopIndex index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters p) throws IOException, SQLException {
    final Parameters output = Parameters.create();
    final int count = p.get("count", 200);
    assert(count > 0);
    final String termKind = p.get("termKind", "lemmas");
    final boolean pullSlices = p.get("pullSlices", false);
    final boolean scoreTerms = p.get("scoreTerms", false);
    final int numTerms = p.get("numTerms", 30);
    final int minTermFrequency = p.get("minTermFrequency", 4);

    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = tokenizer.createDocument("tmp", p.getString("query")).getTerms(termKind);
    output.put("queryTerms", query);

    IntList queryIds = index.translateFromTerms(query);


    final Pair<Long, List<DocumentResult<Integer>>> hits = Timing.milliseconds(() -> {
      try {
        return LocatePhrase.find(index, termKind, query);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    int queryFrequency = hits.right.size();
    System.err.println(hits.left);
    output.put("queryFrequency", queryFrequency);
    output.put("queryTime", hits.left);

    final TIntObjectHashMap<Parameters> hitInfos = new TIntObjectHashMap<>();
    // build slices from the results, based on arguments to this file:
    for (DocumentResult<Integer> hit : hits.right) {
      Parameters doc = Parameters.create();
      doc.put("id", hit.document);
      doc.put("loc", hit.value);
      hitInfos.put(hit.document, doc);
    }

    for (Pair<Integer, String> kv : index.lookupNames(new IntList(hitInfos.keys()))) {
      hitInfos.get(kv.left).put("name", kv.right);
    }

    TIntIntHashMap termProxCounts = new TIntIntHashMap();

    // also pull terms if we want:
    if(scoreTerms || pullSlices) {
      int leftWidth = Math.max(0, p.get("leftWidth", 1));
      int rightWidth = Math.max(0, p.get("rightWidth", 1));

      List<TermSlice> slices = new ArrayList<>(count);
      for (DocumentResult<Integer> result : ListFns.slice(hits.right, 0, count)) {
        int pos = result.value;
        slices.add(new TermSlice(result.document,
            pos - leftWidth, pos + rightWidth + 1));
      }

      for (Pair<TermSlice, IntList> pair : index.pullTermSlices(slices)) {
        if(pullSlices) {
          System.out.println(pair);
          hitInfos.get(pair.left.document).put("terms", pair.right);
        }
        if(scoreTerms) {
          for (int term : pair.right) {
            assert(term >= 0);
            if(queryIds.contains(term)) continue;
            termProxCounts.adjustOrPutValue(term, 1, 1);
          }
        }
      }
    }

    double collectionLength = index.getCollectionLength();
    TopKHeap<PMITerm<Integer>> topTerms = new TopKHeap<>(numTerms);
    if(scoreTerms) {
      termProxCounts.forEachEntry((term, frequency) -> {
        if(frequency > minTermFrequency) {
          topTerms.add(new PMITerm<>(term, index.collectionFrequency(term), queryFrequency, frequency, collectionLength));
        }
        return true;
      });

      IntList termIds = new IntList(numTerms);
      for (PMITerm<Integer> topTerm : topTerms) {
        termIds.add(topTerm.term);
      }
      TIntObjectHashMap<String> terms = new TIntObjectHashMap<>();
      for (Pair<Integer, String> kv : index.lookupTerms(termIds)) {
        terms.put(kv.getKey(), kv.getValue());
      }

      List<Parameters> termResults = new ArrayList<>();
      for (PMITerm<Integer> pmiTerm : topTerms.getUnsortedList()) {
        Parameters tjson = pmiTerm.toJSON();
        tjson.put("term", terms.get(pmiTerm.term));
        //tjson.put("docs", new ArrayList<>(termInfos.get(pmiTerm.term)));
        termResults.add(tjson);
      }
      output.put("termResults", termResults);
    }

    output.put("results", ListFns.slice(new ArrayList<>(hitInfos.valueCollection()), 0, 200));
    return output;
  }
}

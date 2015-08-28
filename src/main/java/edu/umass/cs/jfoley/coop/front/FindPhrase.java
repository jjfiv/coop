package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.random.ReservoirSampler;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.querying.LocatePhrase;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class FindPhrase extends CoopIndexServerFn {
  protected FindPhrase(IndexReader index) {
    super(index);
  }

  public static class TermHitInfo {
    public ReservoirSampler<TermSlice> slices = new ReservoirSampler<TermSlice>(10);
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

    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = tokenizer.createDocument("tmp", p.getString("query")).getTerms(termKind);

    output.put("queryTerms", query);


    final Pair<Long, List<DocumentResult<Integer>>> hits = Timing.milliseconds(() -> LocatePhrase.find(index, query));
    int queryFrequency = hits.right.size();
    System.err.println(hits.left);
    output.put("queryFrequency", queryFrequency);
    output.put("queryTime", hits.left);

    final TIntObjectHashMap<Parameters> hitInfos = new TIntObjectHashMap<>();
    // build slices from the results, based on arguments to this file:
    for (DocumentResult<Integer> hit : ListFns.slice(hits.right, 0, count)) {
      Parameters doc = Parameters.create();
      doc.put("id", hit.document);
      doc.put("loc", hit.value);
      hitInfos.put(hit.document, doc);
    }

    for (Pair<Integer, String> kv : index.lookupNames(new IntList(hitInfos.keys()))) {
      hitInfos.get(kv.left).put("name", kv.right);
    }

    final int numHitsPerTerm = 20;
    HashMap<String, ReservoirSampler<Integer>> termInfos = new HashMap<>();
    //TObjectIntHashMap<String> termProxCounts = new TObjectIntHashMap<>();

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

      for (Pair<TermSlice, List<String>> pair : index.getCorpus().pullTermSlices(slices)) {
        if(pullSlices) {
          hitInfos.get(pair.left.document).put("terms", pair.right);
        }
        if(scoreTerms) {
          for (String term : pair.right) {
            if(query.contains(term)) continue;
            termInfos.computeIfAbsent(term, (k) -> new ReservoirSampler<>(numHitsPerTerm)).add(pair.left.document);
            //termProxCounts.adjustOrPutValue(term, 1, 1);
          }
        }
      }
    }

    double collectionLength = index.getCollectionLength();
    TopKHeap<PMITerm> topTerms = new TopKHeap<>(numTerms);
    if(scoreTerms) {
      for (Map.Entry<String, ReservoirSampler<Integer>> kv : termInfos.entrySet()) {
        String term = kv.getKey();
        int frequency = kv.getValue().total();
        topTerms.add(new PMITerm(term, index.collectionFrequency(term), queryFrequency, frequency, collectionLength));
      }
      /*
      termProxCounts.forEachEntry((term, frequency) -> {
        topTerms.add(new PMITerm(term, index.collectionFrequency(term), queryFrequency, frequency, collectionLength));
        return true;
      });
      */

      List<Parameters> termResults = new ArrayList<>();
      for (PMITerm pmiTerm : topTerms.getUnsortedList()) {
        Parameters tjson = pmiTerm.toJSON();
        tjson.put("docs", new ArrayList<>(termInfos.get(pmiTerm.term)));
        termResults.add(tjson);
      }
      output.put("termResults", termResults);
    }

    output.put("results", new ArrayList<>(hitInfos.valueCollection()));
    return output;
  }
}

package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.front.eval.*;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
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
    final boolean findEntities = p.get("findEntities", false);
    final boolean scoreTerms = p.get("scoreTerms", false);
    final int numTerms = p.get("numTerms", 30);
    final int minTermFrequency = p.get("minTermFrequency", 4);

    final String method = p.get("method", "EvaluatePhrase");
    TermPositionsIndex termIndex = index.getPositionsIndex(termKind);

    FindHitsMethod hitFinder;
    switch (method) {
      case "EvaluatePhrase":
        hitFinder = new EvaluatePhraseMethod(p, output, termIndex);
        break;
      case "LookupSingleTerm":
        hitFinder = new LookupSingleTermMethod(p, output, termIndex);
        break;
      case "LookupSinglePhrase":
        hitFinder = new LookupSinglePhraseMethod(p, output, index);
        break;
      default: throw new IllegalArgumentException("method="+method);
    }

    ArrayList<DocumentResult<Integer>> hits = hitFinder.computeTimed();
    List<DocumentResult<Integer>> topHits = ListFns.slice(hits, 0, 200);

    final TIntObjectHashMap<Parameters> hitInfos = new TIntObjectHashMap<>();
    // only return 200 results
    for (DocumentResult<Integer> hit : topHits) {
      Parameters doc = Parameters.create();
      doc.put("id", hit.document);
      doc.put("loc", hit.value);
      hitInfos.put(hit.document, doc);
    }

    for (Pair<Integer, String> kv : index.lookupNames(new IntList(hitInfos.keys()))) {
      hitInfos.get(kv.left).put("name", kv.right);
    }

    TIntIntHashMap termProxCounts = null;
    int phraseWidth = hitFinder.getPhraseWidth();
    int queryFrequency = hits.size();
    NearbyTermFinder termFinder = new NearbyTermFinder(index, p, output, phraseWidth);

    if(scoreTerms) {
      termProxCounts = termFinder.termCounts(hits);
    }

    long startScoring = System.currentTimeMillis();

    if(termProxCounts != null) {
      PMITermScorer termScorer = new PMITermScorer(termIndex, minTermFrequency, queryFrequency, index.getCollectionLength());
      List<PMITerm<Integer>> topTerms = termScorer.scoreTerms(termProxCounts, numTerms);

      IntList termIds = new IntList(numTerms);
      for (PMITerm<Integer> topTerm : topTerms) {
        termIds.add(topTerm.term);
      }
      long endScoring = System.currentTimeMillis();

      long millisForScoring = (endScoring - startScoring);
      System.out.printf("Spent %d milliseconds scoring terms for %d locations; %1.2f ms/hit; %d candidates.\n", millisForScoring, hits.size(),
          ( (double) millisForScoring / (double) hits.size() ),
          termProxCounts.size());

      TIntObjectHashMap<String> terms = new TIntObjectHashMap<>();
      for (Pair<Integer, String> kv : index.lookupTerms(termIds)) {
        terms.put(kv.getKey(), kv.getValue());
      }

      List<Parameters> termResults = new ArrayList<>();
      for (PMITerm<Integer> pmiTerm : topTerms) {
        Parameters tjson = pmiTerm.toJSON();
        tjson.put("term", terms.get(pmiTerm.term));
        //tjson.put("docs", new ArrayList<>(termInfos.get(pmiTerm.term)));
        termResults.add(tjson);
      }
      output.put("termResults", termResults);
    }

    if(findEntities) {
      long startEntites = System.currentTimeMillis();
      NearbyEntityFinder finder = new NearbyEntityFinder(index, p, output, phraseWidth);
      TIntIntHashMap ecounts = finder.entityCounts(hits);
      long stopEntities = System.currentTimeMillis();
      long millisForScoring = (stopEntities - startEntites);
      System.out.printf("Spent %d milliseconds scoring entities for %d locations; %1.2f ms/hit; %d candidates.\n", millisForScoring, hits.size(),
          ((double) millisForScoring / (double) hits.size()),
          ecounts.size());

      long start = System.currentTimeMillis();
      TIntIntHashMap freq = termIndex.getCollectionFrequencies(new IntList(ecounts.keys()));
      long end = System.currentTimeMillis();
      System.err.println("Pull efrequencies: " + (end - start) + "ms.");

      TopKHeap<PMITerm<Integer>> pmiEntities = new TopKHeap<>(numTerms);
      double collectionLength = index.getCollectionLength();
      ecounts.forEachEntry((eid, frequency) -> {
        if (frequency > minTermFrequency) {
          pmiEntities.add(new PMITerm<>(eid, freq.get(eid), queryFrequency, frequency, collectionLength));
        }
        return true;
      });

      List<Parameters> entities = new ArrayList<>();
      for (PMITerm<Integer> pmiEntity : pmiEntities) {
        IntList eterms = index.getEntitiesIndex().getPhraseVocab().getForward(pmiEntity.term);
        Parameters ep = pmiEntity.toJSON();
        ep.remove("term");
        ep.put("eId", pmiEntity.term);
        ep.put("terms", index.translateToTerms(eterms));
        ep.put("termIds", eterms);
        entities.add(ep);
      }
      output.put("entities", entities);
    }

    // also pull terms if we want:
    if(pullSlices) {
      for (Pair<TermSlice, IntList> pair : termFinder.pullSlicesForSnippets(hits)) {
        Parameters docp = hitInfos.get(pair.left.document);
        docp.put("terms", index.translateToTerms(pair.right));
      }
    }

    output.put("results", ListFns.slice(new ArrayList<>(hitInfos.valueCollection()), 0, 200));
    return output;
  }
}

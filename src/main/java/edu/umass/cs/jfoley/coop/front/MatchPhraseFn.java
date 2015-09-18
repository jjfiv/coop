package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.fn.LazyReduceFn;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsReader;
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
public class MatchPhraseFn extends CoopIndexServerFn {
  public MatchPhraseFn(PhraseHitsReader entities) {
    super(entities.getIndex());
  }

  @Override
  public Parameters handleRequest(Parameters p) throws IOException, SQLException {
    final Parameters output = Parameters.create();
    final int count = p.get("count", 200);
    final boolean pullSlices = p.get("pullSlices", false);
    final boolean scoreTerms = p.get("scoreTerms", false);
    final int numTerms = p.get("numTerms", 30);
    final int minTermFrequency = p.get("minTermFrequency", 4);
    PhrasePositionsIndex entitiesIndex = index.getEntitiesIndex();

    int phraseWidth;
    IntList queryIds;
    List<DocumentResult<Integer>> hits = new ArrayList<>();
    if(p.isLong("phrase")) {
      int phraseId = p.getInt("phrase");
      PostingMover<PositionsList> mover = entitiesIndex.getPositionsMover(phraseId);
      queryIds = index.getEntitiesIndex().phraseVocab.getForward(phraseId);
      phraseWidth = queryIds.size();
      output.put("phraseIds", queryIds);

      long startTime = System.currentTimeMillis();
      mover.execute((docId) -> {
        PositionsList list = mover.getPosting(docId);
        for (int i = 0; i < list.size(); i++) {
          hits.add(new DocumentResult<>(docId, list.getPosition(i)));
        }
      });
      long endTime = System.currentTimeMillis();
      output.put("queryTime", (endTime-startTime));
    } else throw new RuntimeException(p.toString());


    final TIntObjectHashMap<Parameters> hitInfos = new TIntObjectHashMap<>();
    // only return 200 results
    for (DocumentResult<Integer> hit : ListFns.slice(hits, 0, 200)) {
      Parameters doc = Parameters.create();
      doc.put("id", hit.document);
      doc.put("loc", hit.value);
      hitInfos.put(hit.document, doc);
    }

    for (Pair<Integer, String> kv : index.lookupNames(new IntList(hitInfos.keys()))) {
      hitInfos.get(kv.left).put("name", kv.right);
    }

    TIntIntHashMap termProxCounts = new TIntIntHashMap(Math.max(1000, hits.size() / 10));

    long startScoring = System.currentTimeMillis();
    // also pull terms if we want:
    if(scoreTerms || pullSlices) {
      int leftWidth = Math.max(0, p.get("leftWidth", 1));
      int rightWidth = Math.max(0, p.get("rightWidth", 1));

      // Lazy convert hits to slices:
      Iterable<TermSlice> slices = IterableFns.map(hits, (result) -> {
        int pos = result.value;
        TermSlice slice = new TermSlice(result.document,
            pos - leftWidth, pos + rightWidth + phraseWidth);
        assert (slice.size() == leftWidth + rightWidth + phraseWidth);
        return slice;
      });

      Iterable<TermSlice> mergedSlices = IterableFns.lazyReduce(slices, mergeSlicesFn);

      // Lazy pull and calculate most frequent terms:
      for (Pair<TermSlice, IntList> pair : index.pullTermSlices(mergedSlices)) {
        if(pullSlices) {
          Parameters docp = hitInfos.get(pair.left.document);
          if(docp != null) {
            docp.put("terms", index.translateToTerms(pair.right));
          }
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

    int queryFrequency = hits.size();
    output.put("queryFrequency", queryFrequency);

    TermPositionsIndex termIndex = index.getPositionsIndex("lemmas");

    double collectionLength = index.getCollectionLength();
    TopKHeap<PMITerm<Integer>> topTerms = new TopKHeap<>(numTerms);
    if(scoreTerms) {
      long start = System.currentTimeMillis();
      TIntIntHashMap freq = termIndex.getCollectionFrequencies(new IntList(termProxCounts.keys()));
      long end = System.currentTimeMillis();
      System.err.println("Pull frequencies: "+(end-start)+"ms.");

      termProxCounts.forEachEntry((term, frequency) -> {
        int collectionFrequency = freq.get(term);
        if(frequency > collectionFrequency) {
          System.err.println(term+" "+frequency+" "+collectionFrequency);
        }
        if(frequency > minTermFrequency) {
          topTerms.add(new PMITerm<>(term, freq.get(term), queryFrequency, frequency, collectionLength));
        }
        return true;
      });

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

  public static LazyReduceFn<TermSlice> mergeSlicesFn = new LazyReduceFn<TermSlice>() {
    @Override
    public boolean shouldReduce(TermSlice lhs, TermSlice rhs) {
      return (rhs.document == lhs.document) && lhs.end >= rhs.start;
    }

    @Override
    public TermSlice reduce(TermSlice lhs, TermSlice rhs) {
      return new TermSlice(lhs.document, lhs.start, rhs.end);
    }
  };

}

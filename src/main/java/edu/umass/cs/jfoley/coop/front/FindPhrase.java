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

  public static abstract class FindHitsMethod {
    public final Parameters output; // for taking notes on computation.
    public FindHitsMethod(Parameters input, Parameters output) {
      this.output = output;
    }
    public abstract ArrayList<DocumentResult<Integer>> compute() throws IOException;
    public ArrayList<DocumentResult<Integer>> computeTimed() throws IOException {
      long startTime = System.currentTimeMillis();
      ArrayList<DocumentResult<Integer>> hits = compute();
      long endTime = System.currentTimeMillis();
      int queryFrequency = hits.size();
      output.put("queryFrequency", queryFrequency);
      output.put("queryTime", (endTime-startTime));
      return hits;
    }

    public abstract int getPhraseWidth();
    public abstract boolean queryContains(int term);

  }

  public static class EvaluatePhraseMethod extends FindHitsMethod {
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

  public static class LookupSingleTermMethod extends FindHitsMethod {

    private final PostingMover<PositionsList> mover;
    private final String term;
    private int termId;

    public LookupSingleTermMethod(Parameters input, Parameters output, TermPositionsIndex index) throws IOException {
      super(input, output);
      PostingMover<PositionsList> mover = null;
      this.termId = -1;
      if(input.isLong("termId")) {
        this.termId = input.getInt("termId");
        this.term = index.translateToTerm(termId);
      } else if(input.isString("term")) {
        this.term = input.getString("term");
        this.termId = index.translateFromTerm(term);
      } else throw new IllegalArgumentException("Missing argument term=\"the\" or termId=1, etc.");
      mover = index.getPositionsMover(termId);
      this.mover = mover;
    }

    @Override
    public ArrayList<DocumentResult<Integer>> compute() throws IOException {
      ArrayList<DocumentResult<Integer>> hits = new ArrayList<>();
      if(mover == null) return hits;
      mover.execute((docId) -> {
        PositionsList list = mover.getPosting(docId);
        for (int i = 0; i < list.size(); i++) {
          hits.add(new DocumentResult<>(docId, list.getPosition(i)));
        }
      });
      return hits;
    }

    @Override
    public int getPhraseWidth() {
      return 1;
    }

    @Override
    public boolean queryContains(int term) {
      return this.termId == term;
    }
  }


  public static class LookupSinglePhraseMethod extends FindHitsMethod {

    private final PostingMover<PositionsList> mover;
    private final List<String> terms;
    private IntList queryIds;


    public LookupSinglePhraseMethod(Parameters input, Parameters output, CoopIndex index) throws IOException {
      super(input, output);
      PhrasePositionsIndex entitiesIndex = index.getEntitiesIndex();
      int phraseId = input.getInt("phrase");
      this.mover = entitiesIndex.getPositionsMover(phraseId);
      queryIds = entitiesIndex.phraseVocab.getForward(phraseId);
      terms = index.getPositionsIndex("lemmas").translateToTerms(queryIds);

      output.put("phraseIds", queryIds);
      output.put("phraseTerms", terms);
    }

    @Override
    public ArrayList<DocumentResult<Integer>> compute() throws IOException {
      ArrayList<DocumentResult<Integer>> hits = new ArrayList<>();
      if(mover == null) return hits;
      mover.execute((docId) -> {
        PositionsList list = mover.getPosting(docId);
        for (int i = 0; i < list.size(); i++) {
          hits.add(new DocumentResult<>(docId, list.getPosition(i)));
        }
      });
      return hits;
    }

    @Override
    public int getPhraseWidth() {
      return queryIds.size();
    }

    @Override
    public boolean queryContains(int term) {
      return this.queryIds.containsInt(term);
    }
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

    TIntIntHashMap termProxCounts = null;
    if(scoreTerms) termProxCounts = new TIntIntHashMap(Math.max(1000, hits.size() / 10));
    int phraseWidth = hitFinder.getPhraseWidth();
    int queryFrequency = hits.size();

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
        assert(slice.size() == leftWidth+rightWidth+phraseWidth);
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
            if(hitFinder.queryContains(term)) continue;
            termProxCounts.adjustOrPutValue(term, 1, 1);
          }
        }
      }
    }


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


  public static final LazyReduceFn<TermSlice> mergeSlicesFn = new LazyReduceFn<TermSlice>() {
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

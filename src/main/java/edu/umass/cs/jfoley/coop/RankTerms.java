package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.errors.FatalError;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.lang.LazyPtr;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.querying.DocumentAndPosition;
import edu.umass.cs.jfoley.coop.querying.LocatePhrase;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.tokenize.Tokenizer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class RankTerms extends AppFunction {
  @Override
  public String getName() {
    return "rank-terms";
  }

  @Override
  public String getHelpString() {
    return makeHelpStr(
        "index", "path to VocabReader index.",
        "leftWidth", "width of left candidates, [default=5]",
        "rightWidth", "width of right candidates, [default=5]",
        "limit", "number of terms to print out, [default=20]",
        // todo stop results
        // todo sample values instead of doing them all
        "query", "a term or phrase query; we'll tokenize for you; e.g. --query=\"hello world\" or --query=hello"
    );
  }

  public static class PMITerm implements Comparable<PMITerm> {
    public final String term;
    public final int termFrequency; // px, py
    public final int queryFrequency; // py
    public final int queryProxFrequency; // pxy
    public final double collectionLength;
    private final LazyPtr<Double> cachedPMI;

    public PMITerm(String term, int termFrequency, int queryFrequency, int queryProxFrequency, double collectionLength) {
      this.term = term;
      this.termFrequency = termFrequency;
      this.queryFrequency = queryFrequency;
      this.queryProxFrequency = queryProxFrequency;
      this.collectionLength = collectionLength;
      cachedPMI = new LazyPtr<>(this::computePMI);
    }

    public double px() {
      return termFrequency / collectionLength;
    }
    public double py() {
      return queryFrequency / collectionLength;
    }
    public double pxy() {
      return queryProxFrequency / collectionLength;
    }
    private double computePMI() {
      return pxy() / (px()*py());
    }
    public double pmi() {
      return cachedPMI.get();
    }

    @Override
    public int compareTo(PMITerm o) {
      return Double.compare(pmi(), o.pmi());
    }
  }

  @Override
  public void run(Parameters p, PrintStream output) throws Exception {
    IndexReader index = new IndexReader(new Directory(p.getString("index")));
    int leftWidth = p.get("leftWidth", 5);
    assert(leftWidth >= 0);
    int rightWidth = p.get("rightWidth", 5);
    assert(rightWidth >= 0);
    int limit = p.get("limit", 20);
    assert(limit > 0);

    Tokenizer tokenizer = new TagTokenizer();
    List<String> query = tokenizer.tokenize(p.getString("query")).terms;
    System.err.println("I parsed your query as the following terms: "+ StrUtil.join(query, " "));


    Pair<Long, List<DocumentAndPosition>> hits = Timing.milliseconds(() -> LocatePhrase.find(index, query));
    int queryFrequency = hits.right.size();
    System.err.println("Run query in "+hits.left+" ms. "+hits.right.size()+" hits found!");

    // build slices from the results, based on arguments to this file:
    List<TermSlice> slices = new ArrayList<>();
    for (DocumentAndPosition hit : ListFns.take(hits.right, limit)) {
      slices.add(new TermSlice(
          hit.documentId,
          hit.matchPosition - leftWidth,
          hit.matchPosition + query.size() + rightWidth));
    }

    // Now score the nearby terms!
    final TObjectIntHashMap<String> termProxCounts = new TObjectIntHashMap<>();
    long candidateFindingTime = Timing.milliseconds(() -> {
      for (TermSlice slice : slices) {
        for (String term : index.pullTermIdSlice(slice)) {
          termProxCounts.adjustOrPutValue(term, 1, 1);
        }
      }
    });
    System.err.println("Found "+termProxCounts.size() + " candidates in "+candidateFindingTime+" ms.");

    // Okay, now actually score these candidates!
    double collectionLength = index.getCollectionLength();
    TopKHeap<PMITerm> topTerms = new TopKHeap<>(limit, Comparing.defaultComparator());

    long scoringTime = Timing.milliseconds(() -> {
      // Now lookup collection frequencies, this is p_x to termProxCounts p_xy
      termProxCounts.forEachEntry((term, frequency) -> {
        // skip query itself.
        if(query.contains(term)) return true;
        try {
          topTerms.add(new PMITerm(
              term,
              index.collectionFrequency(term),
              queryFrequency,
              frequency,
              collectionLength));
        } catch (IOException e) {
          throw new FatalError(e);
        }
        return true;
      });
    });
    System.err.println("Scored " + termProxCounts.size() + " candidates in " + scoringTime + " ms.");

    for (PMITerm pmiTerm : topTerms.getSorted()) {
      System.out.printf("%-20s %1.4f\n", StrUtil.replaceUnicodeQuotes(pmiTerm.term), pmiTerm.pmi());
    }

  }
}

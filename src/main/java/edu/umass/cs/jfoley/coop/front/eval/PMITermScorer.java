package edu.umass.cs.jfoley.coop.front.eval;

import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.IOException;
import java.util.List;

/**
 * @author jfoley
 */
public class PMITermScorer {
  public final TermPositionsIndex termIndex;
  public final int minTermFrequency;
  public final int queryFrequency;
  public final double collectionLength;

  public PMITermScorer(TermPositionsIndex termIndex, int minTermFrequency, int queryFrequency, double collectionLength) {
    this.termIndex = termIndex;
    this.minTermFrequency = minTermFrequency;
    this.queryFrequency = queryFrequency;
    this.collectionLength = collectionLength;
  }

  public List<PMITerm<Integer>> scoreTerms(TIntIntHashMap termProxCounts, int numTerms) throws IOException {
    long start = System.currentTimeMillis();
    TIntIntHashMap freq = termIndex.getCollectionFrequencies(new IntList(termProxCounts.keys()));
    long end = System.currentTimeMillis();
    System.err.println("Pull frequencies: "+(end-start)+"ms.");

    TopKHeap<PMITerm<Integer>> topTerms = new TopKHeap<>(numTerms);
    termProxCounts.forEachEntry((term, frequency) -> {
      int collectionFrequency = freq.get(term);
      if (frequency > collectionFrequency) {
        System.err.println(term + " " + frequency + " " + collectionFrequency);
      }
      if (frequency > minTermFrequency) {
        topTerms.add(new PMITerm<>(term, freq.get(term), queryFrequency, frequency, collectionLength));
      }
      return true;
    });

    return topTerms.getUnsortedList();
  }
}

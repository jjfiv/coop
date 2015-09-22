package edu.umass.cs.jfoley.coop.front.eval;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.fn.LazyReduceFn;
import edu.umass.cs.jfoley.coop.front.CoopIndex;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import gnu.trove.map.hash.TIntIntHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author jfoley
 */
public class NearbyTermFinder {
  public final CoopIndex index;
  private final Parameters output;
  public final int leftWidth;
  public final int rightWidth;
  public final int phraseWidth;

  public NearbyTermFinder(CoopIndex index, Parameters input, Parameters output, int phraseWidth) {
    this.index = index;
    this.output = output;
    leftWidth = Math.max(0, input.get("leftWidth", 1));
    rightWidth = Math.max(0, input.get("rightWidth", 1));
    this.phraseWidth = phraseWidth;
  }

  public Iterable<TermSlice> hitsToSlices(Iterable<DocumentResult<Integer>> items) {
    return IterableFns.map(items, (result) -> {
      int pos = result.value;
      TermSlice slice = new TermSlice(result.document,
          pos - leftWidth, pos + rightWidth + phraseWidth);
      assert (slice.size() == leftWidth + rightWidth + phraseWidth);
      return slice;
    });
  }

  public Iterable<Pair<TermSlice, IntList>> pullSlicesForTermScoring(Iterable<TermSlice> input) {
    Iterable<TermSlice> mergedSlices = IterableFns.lazyReduce(input, mergeSlicesFn);
    return index.pullTermSlices(mergedSlices);
  }

  public TIntIntHashMap termCounts(Iterable<Pair<TermSlice, IntList>> slices) {
    TIntIntHashMap counts = new TIntIntHashMap();
    for (Pair<TermSlice, IntList> slice : slices) {
      IntList rhs = slice.getValue();
      for (int i = 0; i < rhs.size(); i++) {
        counts.adjustOrPutValue(rhs.getQuick(i), 1, 1);
      }
    }
    return counts;
  }

  public HashMap<Integer, List<TermSlice>> slicesByDocument(Iterable<TermSlice> slices) {
    HashMap<Integer, List<TermSlice>> slicesByDocument = new HashMap<>();
    Iterable<TermSlice> mergedSlices = IterableFns.lazyReduce(slices, mergeSlicesFn);
    for (TermSlice slice : mergedSlices) {
      MapFns.extendCollectionInMap(slicesByDocument, slice.document, slice, (GenerateFn<List<TermSlice>>) ArrayList::new);
      //slicesByDocument.computeIfAbsent(slice.document, (ignored) -> new ArrayList()).add(slice);
    }
    return slicesByDocument;
  }


  public TIntIntHashMap termCounts(List<DocumentResult<Integer>> hits) throws IOException {
    return termCounts(pullSlicesForTermScoring(hitsToSlices(hits)));
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

  public Iterable<Pair<TermSlice, IntList>> pullSlicesForSnippets(ArrayList<DocumentResult<Integer>> hits) {
    return index.pullTermSlices(hitsToSlices(hits));
  }
}

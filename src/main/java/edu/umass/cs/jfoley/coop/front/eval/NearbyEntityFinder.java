package edu.umass.cs.jfoley.coop.front.eval;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.front.CoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitList;
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
public class NearbyEntityFinder extends NearbyTermFinder {
  public final CoopIndex index;
  public final PhrasePositionsIndex entities;
  public NearbyEntityFinder(CoopIndex index, Parameters input, Parameters output, int phraseWidth) {
    super(index, input, output, phraseWidth);
    this.index = index;
    entities = index.getEntitiesIndex();
  }

  public HashMap<Integer, List<TermSlice>> slicesByDocument(Iterable<TermSlice> slices) {
    HashMap<Integer, List<TermSlice>> slicesByDocument = new HashMap<>();
    Iterable<TermSlice> mergedSlices = IterableFns.lazyReduce(slices, mergeSlicesFn);
    for (TermSlice slice : mergedSlices) {
      slicesByDocument.computeIfAbsent(slice.document, (ignored) -> new ArrayList()).add(slice);
    }
    return slicesByDocument;
  }

  public TIntIntHashMap entityCounts(Iterable<TermSlice> slices) throws IOException {
    IOMap<Integer, PhraseHitList> documentHits = entities.getPhraseHits().getDocumentHits();
    HashMap<Integer, List<TermSlice>> slicesByDocument = slicesByDocument(slices);
    TIntIntHashMap ecounts = new TIntIntHashMap();

    System.out.println(slicesByDocument);
    TIntIntHashMap sizeFreqs = new TIntIntHashMap();

    for (Pair<Integer, PhraseHitList> pair : documentHits.getInBulk(new IntList(slicesByDocument.keySet()))) {
      int doc = pair.getKey();
      PhraseHitList hits = pair.getValue();

      List<TermSlice> localSlices = slicesByDocument.get(doc);
      for (TermSlice slice : localSlices) {
        IntList eids = hits.find(slice.start, slice.size());
        for (int i = 0; i < eids.size(); i++) {
          ecounts.adjustOrPutValue(eids.getQuick(i), 1, 1);
        }
      }
    }

    return ecounts;
  }


  public TIntIntHashMap entityCounts(ArrayList<DocumentResult<Integer>> hits) throws IOException {
    return entityCounts(hitsToSlices(hits));
  }
}

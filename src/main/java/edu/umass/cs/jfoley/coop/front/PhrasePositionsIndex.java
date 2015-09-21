package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * @author jfoley
 */
public class PhrasePositionsIndex {
  final IdMaps.Reader<String> termVocab;
  final IdMaps.Reader<IntList> phraseVocab;
  final IOMap<Integer, PostingMover<PositionsList>> positions;
  HashMap<Integer, PositionsCountMetadata> pmeta;

  public PhrasePositionsIndex(IdMaps.Reader<String> termVocab, IdMaps.Reader<IntList> phraseVocab, IOMap<Integer, PostingMover<PositionsList>> positions) throws IOException {
    this.termVocab = termVocab;
    this.phraseVocab = phraseVocab;
    this.positions = positions;

    long start = System.currentTimeMillis();
    pmeta = new HashMap<>();
    for (Pair<Integer, PostingMover<PositionsList>> kv : this.positions.items()) {
      pmeta.put(kv.getKey(), (PositionsCountMetadata) kv.getValue().getMetadata());
    }
    long end = System.currentTimeMillis();
    System.out.println("Caching metadata: " + (end - start) + "ms.");
  }


  public PostingMover<PositionsList> getPositionsMover(int phraseId) throws IOException {
    if (phraseId < 0) return null;
    return positions.get(phraseId);
  }
  PostingMover<PositionsList> getPositionsMover(IntList termIds) throws IOException {
    assert(!termIds.isEmpty());
    if(termIds.containsInt(-1)) return null;
    Integer phraseId = phraseVocab.getReverse(termIds);
    if(phraseId == null) return null;
    return getPositionsMover(phraseId);
  }
  PostingMover<PositionsList> getPositionsMover(List<String> terms) throws IOException {
    IntList termIds = new IntList(terms.size());
    for (String term : terms) {
      Integer id = termVocab.getReverse(term);
      if(id == null || id < 0) return null;
      termIds.add(id);
    }
    return getPositionsMover(termIds);
  }

  public PositionsCountMetadata getMetadata(int phraseId) throws IOException {
    return pmeta.get(phraseId);
  }

  public IdMaps.Reader<IntList> getPhraseVocab() {
    return phraseVocab;
  }
}

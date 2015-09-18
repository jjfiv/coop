package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.coders.map.impl.WaltzDiskMapReader;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.bills.ZeroTerminatedIds;
import edu.umass.cs.jfoley.coop.front.CoopIndex;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import gnu.trove.set.hash.TIntHashSet;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author jfoley
 */
public class PhraseHitsReader implements Closeable {
  private final Directory baseDir;
  private final String baseName;
  final WaltzDiskMapReader<Integer, PhraseHitList> docHits;
  final IdMaps.Reader<IntList> vocab;
  final IntCoopIndex index;
  /** find documents by phrase id */
  final IOMap<Integer, PostingMover<PositionsList>> documentsByPhrase;
  /** find phrases by term ids */
  final IOMap<Integer, PostingMover<PositionsList>> phrasesByTerm;

  final TermPositionsIndex phrasesByTermIndex;

  public PhraseHitsReader(IntCoopIndex index, Directory input, String baseName) throws IOException {
    this.baseDir = input;
    this.baseName = baseName;
    this.index = index;
    docHits = new WaltzDiskMapReader<>(input, baseName+".dochits", FixedSize.ints, new PhraseHitListCoder());
    vocab = GalagoIO.openIdMapsReader(input.childPath(baseName + ".vocab"), FixedSize.ints, new ZeroTerminatedIds());
    documentsByPhrase = PhraseHitsWriter.cfg.openReader(input, baseName + ".positions");
    phrasesByTerm = PhraseHitsWriter.cfg.openReader(input, baseName + ".index");

    phrasesByTermIndex = new TermPositionsIndex(index.getTermVocabulary(), phrasesByTerm);
    //documentsByPhraseIndex = new CoopIndex.PositionsIndex<>(vocab, documentsByPhrase);

    TIntHashSet wordToEntity = new TIntHashSet();
    long start = System.currentTimeMillis();
    for (IntList key : vocab.values()) {
      //wordToEntity.addAll(key.asArray());
      wordToEntity.add(key.getQuick(0));
    }
    System.err.println("# entity-trigger-words: " + wordToEntity.size());
    long end = System.currentTimeMillis();
    System.out.println("Entity autocomplete: "+(end-start)+"ms.");
  }

  @Override
  public String toString() {
    return "PhraseHitsReader("+baseDir.childPath(baseName)+")";
  }

  @Override
  public void close() throws IOException {
    docHits.close();
    vocab.close();;
    documentsByPhrase.close();
  }

  public CoopIndex getIndex() {
    return index;
  }

  public TermPositionsIndex getPhrasesByTerm() { return phrasesByTermIndex; }
  //public CoopIndex.PositionsIndex<String> getDocumentsByPhrase() { return documentsByPhrase; }

}

package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.IntListCoder;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.KeyMetadata;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.bills.ZeroTerminatedIds;
import edu.umass.cs.jfoley.coop.front.CoopIndex;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * @author jfoley
 */
public class PhraseHitsReader implements Closeable {
  private final Directory baseDir;
  private final String baseName;
  final IOMap<Integer, PhraseHitList> docHits;
  final IdMaps.Reader<IntList> vocab;
  final IntCoopIndex index;
  /** find documents by phrase id */
  final IOMap<Integer, PostingMover<PositionsList>> documentsByPhrase;
  /** find phrases by term ids */
  final IOMap<Integer, PostingMover<PositionsList>> phrasesByTerm;

  final TermPositionsIndex phrasesByTermIndex;
  private IOMap<Integer, IntList> ambiguousMap;

  public PhraseHitsReader(IntCoopIndex index, Directory input, String baseName) throws IOException {
    this.baseDir = input;
    this.baseName = baseName;
    this.index = index;
    docHits = GalagoIO.openIOMap(input, baseName + ".dochits", FixedSize.ints, new PhraseHitListCoder());
    vocab = GalagoIO.openIdMapsReader(input.childPath(baseName + ".vocab"), FixedSize.ints, new ZeroTerminatedIds());
    documentsByPhrase = PhraseHitsWriter.cfg.openReader(input, baseName + ".positions");
    phrasesByTerm = PhraseHitsWriter.cfg.openReader(input, baseName + ".index");

    File ambigIndexFile = input.child(baseName+".ambiguous");
    if(ambigIndexFile.exists()) {
      ambiguousMap = GalagoIO.openIOMap(FixedSize.ints, IntListCoder.instance, ambigIndexFile.getPath());
    }

    phrasesByTermIndex = new TermPositionsIndex(index.getTermVocabulary(), null, phrasesByTerm, index.getTokenizer(), index.getCorpus());
    //documentsByPhraseIndex = new CoopIndex.PositionsIndex<>(vocab, documentsByPhrase);
  }

  @Override
  public String toString() {
    return "PhraseHitsReader("+baseDir.childPath(baseName)+")";
  }

  @Override
  public void close() throws IOException {
    docHits.close();
    vocab.close();
    documentsByPhrase.close();
  }

  public CoopIndex getIndex() {
    return index;
  }

  public TermPositionsIndex getPhrasesByTerm() { return phrasesByTermIndex; }

  public IdMaps.Reader<IntList> getPhraseVocab() {
    return vocab;
  }

  public IOMap<Integer, PhraseHitList> getDocumentHits() {
    return docHits;
  }

  @Nullable
  public IOMap<Integer, IntList> getAmbiguousPhrases() {
    return ambiguousMap;
  }

  public IOMap<Integer, PostingMover<PositionsList>> getDocumentsByPhrase() {
    return documentsByPhrase;
  }
  public PositionsCountMetadata getPhraseMetadata(int phraseId) throws IOException {
    PostingMover<PositionsList> mover = documentsByPhrase.get(phraseId);
    if(mover == null) {
      return null;
    }
    KeyMetadata<?> meta = mover.getMetadata();
    if(meta instanceof PositionsCountMetadata) {
      return (PositionsCountMetadata) meta;
    }
    return null;
  }
}

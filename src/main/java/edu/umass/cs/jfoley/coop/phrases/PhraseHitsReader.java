package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.map.impl.WaltzDiskMapReader;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.bills.ZeroTerminatedIds;
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
  final WaltzDiskMapReader<Integer, PostingMover<PositionsList>> postings;

  public PhraseHitsReader(IntCoopIndex index, Directory input, String baseName) throws IOException {
    this.baseDir = input;
    this.baseName = baseName;
    this.index = index;
    docHits = new WaltzDiskMapReader<>(input, baseName+".dochits", FixedSize.ints, new PhraseHitListCoder());
    vocab = GalagoIO.openIdMapsReader(input.childPath(baseName + ".vocab"), FixedSize.ints, new ZeroTerminatedIds());
    postings = PhraseHitsWriter.cfg.openReader(input, baseName+".positions");

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
    postings.close();
  }
}

package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.map.impl.WaltzDiskMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.PostingsConfig;
import edu.umass.cs.ciir.waltz.sys.positions.AccumulatingPositionsWriter;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;
import edu.umass.cs.jfoley.coop.bills.ZeroTerminatedIds;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class PhraseHitsWriter implements Closeable {
  public static final PostingsConfig<Integer, PositionsList> cfg = new PostingsConfig<>(
      FixedSize.ints,
      new PositionsListCoder(),
      Comparing.defaultComparator(),
      new PositionsCountMetadata()
  );

  public final Directory outputDir;
  public final String baseName;
  public final AccumulatingHitListWriter byDocHitsWriter;
  public final AccumulatingPositionsWriter<Integer> writer;
  public final HashMap<IntList, Integer> vocab;


  public PhraseHitsWriter(Directory outputDir, String baseName) throws IOException {
    this.outputDir = outputDir;
    this.baseName = baseName;
    vocab = new HashMap<>();
    byDocHitsWriter = new AccumulatingHitListWriter(new WaltzDiskMapWriter<>(outputDir, baseName+".dochits", FixedSize.ints, new PhraseHitListCoder()));
    writer = cfg.getPositionsWriter(outputDir, baseName+".positions");
  }

  public void onPhraseHit(int docId, int start, int size, IntList slice) {
    int id = MapFns.getOrInsert(vocab, slice);
    writer.add(id, docId, start);
    byDocHitsWriter.add(docId, start, size, id);
  }

  @Override
  public void close() throws IOException {
    try (IdMaps.Writer<IntList> phraseVocabWriter = GalagoIO.openIdMapsWriter(outputDir.childPath(baseName+".vocab"), FixedSize.ints, new ZeroTerminatedIds())) {
      for (Map.Entry<IntList, Integer> kv : vocab.entrySet()) {
        phraseVocabWriter.put(kv.getValue(), kv.getKey());
      }
    }
    writer.close();
    byDocHitsWriter.close();
  }

  }

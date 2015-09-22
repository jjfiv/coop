package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.Pair;
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
import java.util.ArrayList;
import java.util.Collections;
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
  public final AccumulatingPositionsWriter<Integer> docPositionsWriter;
  public final HashMap<IntList, Integer> vocab;


  public PhraseHitsWriter(Directory outputDir, String baseName) throws IOException {
    this.outputDir = outputDir;
    this.baseName = baseName;
    vocab = new HashMap<>();
    byDocHitsWriter = new AccumulatingHitListWriter(new WaltzDiskMapWriter<>(outputDir, baseName+".dochits", FixedSize.ints, new PhraseHitListCoder()));
    docPositionsWriter = cfg.getPositionsWriter(outputDir, baseName+".positions");
  }

  public void onPhraseHit(int id, int docId, int start, int size, IntList slice) {
    if(id == -1) {
      // internal vocabulary ids:
      id = MapFns.getOrInsert(vocab, slice);
    } else {
      // external vocabulary ids:
      vocab.put(slice, id);
    }
    docPositionsWriter.add(id, docId, start);
    byDocHitsWriter.add(docId, start, size, id);
  }

  @Override
  public void close() throws IOException {
    try (IdMaps.Writer<IntList> phraseVocabWriter = GalagoIO.openIdMapsWriter(outputDir.childPath(baseName+".vocab"), FixedSize.ints, new ZeroTerminatedIds());
         AccumulatingPositionsWriter<Integer> phrasePositionsWriter = cfg.getPositionsWriter(outputDir, baseName + ".index")) {

      ArrayList<Pair<Integer, IntList>> docs = new ArrayList<>(vocab.size());
      for (Map.Entry<IntList, Integer> kv : vocab.entrySet()) {
        IntList words = kv.getKey();
        int phraseId = kv.getValue();
        docs.add(Pair.of(phraseId, words));
      }
      Collections.sort(docs, Pair.<Integer,IntList>cmpLeft());

      for (Pair<Integer, IntList> item : docs) {
        int phraseId = item.left;
        IntList words = item.right;
        phraseVocabWriter.put(phraseId, words);
        for (int j = 0; j < words.size(); j++) {
          // word, doc, pos
          phrasePositionsWriter.add(words.getQuick(j), phraseId, j);
        }
      }
    }

    docPositionsWriter.close();
    byDocHitsWriter.close();
  }
}

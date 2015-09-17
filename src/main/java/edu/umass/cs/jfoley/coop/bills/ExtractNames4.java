package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.map.impl.WaltzDiskMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.phrases.AccumulatingHitListWriter;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitListCoder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author jfoley
 */
public class ExtractNames4 {

  ;


  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("robust.ints"));

    Debouncer msg2 = new Debouncer(3000);
    // load up precomputed queries:
    Pattern spaces = Pattern.compile("\\s+");
    HashMap<IntList, Integer> vocab = new HashMap<>();
    try (
        IdMaps.Writer<IntList> phraseVocabWriter = GalagoIO.openIdMapsWriter(target.baseDir.childPath("entities.vocab"), FixedSize.ints, new ZeroTerminatedIds());
        AccumulatingHitListWriter byDocHitsWriter = new AccumulatingHitListWriter(new WaltzDiskMapWriter<>(target.baseDir, "entities.dochits", FixedSize.ints, new PhraseHitListCoder()))
        ) {
      try (LinesIterable lines = LinesIterable.fromFile(target.baseDir.child("dbpedia.titles.hits.gz"))) {
        for (String line : lines) {
          String[] col = spaces.split(line);
          int doc = Integer.parseInt(col[0]);
          int hitStart = Integer.parseInt(col[1]);
          int hitSize = Integer.parseInt(col[2]);

          IntList words = new IntList(col.length - 3);
          for (int i = 3; i < col.length; i++) {
            words.push(Integer.parseInt(col[i]));
          }

          int id = MapFns.getOrInsert(vocab, words);
          byDocHitsWriter.add(doc, hitStart, hitSize, id);

          if (msg2.ready()) {
            System.err.println("Processing lines: " + msg2.estimate(lines.getLineNumber()));
            System.err.println("Processing docs: " + msg2.estimate(doc, 529000));
          }
        }
      }
      System.err.println("# begin writing!");

      // write vocab:
      for (Map.Entry<IntList, Integer> kv : vocab.entrySet()) {
        phraseVocabWriter.put(kv.getValue(), kv.getKey());
      }
    }
    System.err.println("# end writing!");
  }
}

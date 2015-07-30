package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.archive.ZipArchive;
import ciir.jfoley.chai.io.archive.ZipArchiveEntry;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.general.NamespacedLabel;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author jfoley
 */
public class CoopDocIndexer {
  public static void main(String[] args) throws IOException {
    System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "10");

    Directory output = new Directory("clue_pre1.index");
    List<File> inputZips = new ArrayList<>();
    List<File> candidates = Directory.Read(".").children();
    for (File candidate : candidates) {
      if(candidate.getName().startsWith("clue_schemax")) {
        inputZips.add(candidate);
      }
    }
    KryoCoder<CoopDoc> coder = new KryoCoder<>(CoopDoc.class);

    Debouncer msg = new Debouncer(1000);

    long startTime = System.currentTimeMillis();
    try (TermBasedIndexWriter builder = new TermBasedIndexWriter(output)) {
      for (File inputZip : inputZips) {
        try (ZipArchive zip = ZipArchive.open(inputZip)) {
          List<ZipArchiveEntry> listEntries = zip.listEntries();
          for (int i = 0; i < listEntries.size(); i++) {
            ZipArchiveEntry entry = listEntries.get(i);
            CoopDoc doc = coder.read(entry.getInputStream());
            if(msg.ready()) {
              System.err.println(i + "/" + listEntries.size() + " " + doc.getName());
              System.err.println("# "+msg.estimate(i, listEntries.size()));
            }
            builder.addDocument(doc);
            if(i >= 10) break;
          }
        }
      }
      long endParsingTime = System.currentTimeMillis();
      System.out.println("Total parsing time: "+(endParsingTime - startTime)+"ms.");
    }
    long endTime = System.currentTimeMillis();
    System.out.println("Total time: "+(endTime - startTime)+"ms.");
    // 27.6s i>=50
    // 17.2s i>=50 with threaded Sorter
    // parse: 189,280ms, total: 239,167ms i>=500

    try (TermBasedIndexReader reader = new TermBasedIndexReader(output)) {
      PostingMover<Integer> mover = reader.sentencesByTerms.get(new NamespacedLabel("lemmas", "the"));
      assert(mover != null);
      TIntIntHashMap freqs = new TIntIntHashMap();
      mover.execute((id) -> {
        assert(mover.matches(id));
        Integer x = mover.getCurrentPosting();
        assert(x != null);
        freqs.adjustOrPutValue(Objects.requireNonNull(x), 1, 1);
      });

      System.out.println("lemmas:the stats"+freqs);
    }
  }
}

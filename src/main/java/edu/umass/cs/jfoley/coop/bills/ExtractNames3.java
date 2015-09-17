package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.io.postings.ArrayPosList;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.PostingsConfig;
import edu.umass.cs.ciir.waltz.sys.positions.PIndexWriter;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author jfoley
 */
public class ExtractNames3 {

  public static class AccumulatingPositionsWriter<K> implements Closeable {
    int lastDoc = -1;
    HashMap<K, IntList> docHits = new HashMap<>();

    public final PIndexWriter<K,PositionsList> output;

    public AccumulatingPositionsWriter(PIndexWriter<K, PositionsList> output) {
      this.output = output;
    }

    public void add(K word, int doc, int pos) {
      if(lastDoc != doc) {
        flush();
      }
      lastDoc = doc;
      MapFns.extendCollectionInMap(docHits, word, pos, (GenerateFn<IntList>) IntList::new);
    }

    private void flush() {
      if(lastDoc != -1) {
        for (Map.Entry<K, IntList> kv : docHits.entrySet()) {
          output.add(kv.getKey(), lastDoc, new ArrayPosList(kv.getValue()));
        }
        docHits.clear();
      }
    }

    @Override
    public void close() throws IOException {
      flush();
      output.close();
    }
  }

  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("robust.ints"));

    PostingsConfig<IntList, PositionsList> cfg = new PostingsConfig<>(
        new ZeroTerminatedIds(),
        new PositionsListCoder(),
        new IntListTrie.IntListCmp(),
        new PositionsCountMetadata()
    );

    Debouncer msg2 = new Debouncer(3000);
    // load up precomputed queries:
    Pattern spaces = Pattern.compile("\\s+");
    try (AccumulatingPositionsWriter<IntList> writer = new AccumulatingPositionsWriter<>(cfg.getWriter(target.baseDir, "dbpedia.positions"))) {
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

          writer.add(words, doc, hitStart);

          if(msg2.ready()) {
            System.err.println("Processing lines: "+ msg2.estimate(lines.getLineNumber()));
            System.err.println("Processing docs: "+ msg2.estimate(doc, 529000));
          }
        }
      }
      System.err.println("# begin writing!");
    }
    System.err.println("# end writing!");
  }

}

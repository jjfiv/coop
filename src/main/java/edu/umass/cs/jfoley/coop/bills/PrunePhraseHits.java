package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.IOException;

/**
 * @author jfoley
 */
public class PrunePhraseHits {
  public static void main(String[] args) throws IOException {
    // probably don't care about phrases that only occur 1x, 2x, etc. Let's create statistics for each:
    int[] counts = new int[57_892_545+1];
    Debouncer msg = new Debouncer();
    int N = 474800983;
    try (LinesIterable lines = LinesIterable.fromFile("bills.ints/phrase.hits.gz")) {
      for (String line : lines) {
        int phraseId = Integer.parseInt(StrUtil.takeBefore(line, " "));
        counts[phraseId]++;
        if(msg.ready()) {
          System.err.println("# rate: "+msg.estimate(lines.getLineNumber(), N));
        }
        /*int termId = Integer.parseInt(cols[0]);
        IntList slice = new IntList(cols.length-1);
        for (int i = 1; i < cols.length; i++) {
          slice.add(Integer.parseInt(cols[i]));
        }*/
      }
    }

    TIntIntHashMap freqs = new TIntIntHashMap();
    for (int freq : counts) {
      freqs.adjustOrPutValue(freq, 1, 1);
    }
    freqs.forEachEntry((freq, count) -> {
      System.err.println(freq+"\t"+count);
      return true;
    });
  }
}

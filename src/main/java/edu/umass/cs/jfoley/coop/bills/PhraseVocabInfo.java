package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.IOException;

/**
 * @author jfoley
 */
public class PhraseVocabInfo {
  public static void main(String[] args) throws IOException {
    Debouncer msg = new Debouncer(1000);
    TIntIntHashMap lengthHist = new TIntIntHashMap();
    int N = 57892545;
    try (LinesIterable lines = LinesIterable.fromFile("bills.ints/phrase.vocab.gz")) {
      for (String line : lines) {
        String[] cols = line.split("\\s+");
        lengthHist.adjustOrPutValue(cols.length-1, 1, 1);
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

    System.out.println(lengthHist);
  }
}

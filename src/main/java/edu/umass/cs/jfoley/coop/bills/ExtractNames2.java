package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author jfoley
 */
public class ExtractNames2 {

  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("robust.ints"));

    long start, end;

    // load up precomputed queries:
    start = System.currentTimeMillis();
    PhraseDetector detector = PhraseDetector.loadFromTextFile(20, target.baseDir.child("dbpedia.titles.intq.gz"));
    end = System.currentTimeMillis();
    System.out.println("loading titles: "+(end-start)+"ms.");

    Debouncer msg2 = new Debouncer(500);
    StreamingStats hitsPerDoc = new StreamingStats();
    long globalPosition = 0;
    try (PrintWriter hits = IO.openPrintWriter(target.baseDir.childPath("dbpedia.titles.hits.2.gz"))) {
      int ND = target.corpus.numberOfDocuments();
      for (int docIndex = 0; docIndex < ND; docIndex++) {
        int[] doc = target.corpus.getDocument(docIndex);
        final int docId = docIndex;

        StringBuilder outputForThisDoc = new StringBuilder();
        int hitcount = detector.match(doc, (hitStart, hitSize) -> {
          //hits.printf("%d\t%d\t%d\t%s\n", docIndex, position-i, i+1, buffer);
          outputForThisDoc
              .append(docId).append('\t')
              .append(hitStart).append('\t')
              .append(hitSize).append('\t');

          assert(hitStart >= 0);
          assert(hitSize >= 1);

          for (int termIndex = 0; termIndex < hitSize; termIndex++) {
            outputForThisDoc
                .append(doc[termIndex+hitStart])
                .append(' ');
          }
          outputForThisDoc.append('\n');
        });

        hits.print(outputForThisDoc.toString());
        globalPosition += doc.length;
        hitsPerDoc.push(hitcount);

        if(msg2.ready()) {
          System.out.println("docIndex="+docIndex);
          System.out.println("NERing documents at: "+msg2.estimate(docIndex, ND));
          System.out.println("NERing documents at terms/s: "+msg2.estimate(globalPosition));
          System.out.println("hitsPerDoc: "+hitsPerDoc);
        }
      }
    }

  }
}

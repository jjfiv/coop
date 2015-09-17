package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.CircularIntBuffer;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author jfoley
 */
public class NERIndex {

  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("robust.ints"));

    long start, end;

    int N = 20;
    ArrayList<HashSet<List<Integer>>> matchingBySize = new ArrayList<>(N);
    for (int i = 0; i < N; i++) { matchingBySize.add(new HashSet<>(60000)); }

    // load up precomputed queries:
    Pattern spaces = Pattern.compile("\\s+");
    start = System.currentTimeMillis();
    try (LinesIterable lines = LinesIterable.fromFile(target.baseDir.child("dbpedia.titles.intq.gz"))) {
      for (String line : lines) {
        String[] col = spaces.split(line);
        int n = col.length;
        if(n > N) continue;
        IntList pattern = new IntList(n);
        for (String str : col) {
          pattern.add(Integer.parseInt(str));
        }
        matchingBySize.get(n-1).add(pattern);
      }
    }
    end = System.currentTimeMillis();
    System.out.println("loading titles: "+(end-start)+"ms.");


    ArrayList<CircularIntBuffer> patternBuffers = new ArrayList<>(N);
    for (int i = 0; i < N; i++) {
      patternBuffers.add(new CircularIntBuffer(i+1));
    }

    Debouncer msg2 = new Debouncer(500);
    StreamingStats hitsPerDoc = new StreamingStats();
    long globalPosition = 0;
    try (PrintWriter hits = IO.openPrintWriter(target.baseDir.childPath("dbpedia.titles.hits.gz"))) {
      int ND = target.corpus.numberOfDocuments();
      for (int docIndex = 0; docIndex < ND; docIndex++) {
        int[] doc = target.corpus.getDocument(docIndex);

        StringBuilder outputForThisDoc = new StringBuilder();
        for (int i = 0; i < N; i++) {
          CircularIntBuffer buffer = patternBuffers.get(i);
          buffer.clear();
        }
        int hitcount = 0;
        for (int position = 0; position < doc.length; position++) {
          int term = doc[position];

          for (int i = 0; i < N; i++) {
            CircularIntBuffer buffer = patternBuffers.get(i);
            buffer.add(term);
            if (buffer.full() && matchingBySize.get(i).contains(buffer)) {
              int hitStart = position-i;
              int hitSize = i+1;

              //hits.printf("%d\t%d\t%d\t%s\n", docIndex, position-i, i+1, buffer);
              outputForThisDoc
                  .append(docIndex).append('\t')
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

              hitcount++;
            }
          }
        }
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

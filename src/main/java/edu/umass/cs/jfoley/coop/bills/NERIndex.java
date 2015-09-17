package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.FixedSlidingWindow;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
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
    for (int i = 0; i < N; i++) { matchingBySize.add(new HashSet<>()); }

    // load up precomputed queries:
    Pattern spaces = Pattern.compile("\\s+");
    start = System.currentTimeMillis();
    try (LinesIterable lines = LinesIterable.fromFile(target.baseDir.child("dbpedia.titles.intq.gz"))) {
      for (String line : lines) {
        String[] col = spaces.split(line);
        int n = col.length;
        IntList pattern = new IntList(n);
        for (String str : col) {
          pattern.add(Integer.parseInt(str));
        }
        matchingBySize.get(n-1).add(pattern);
      }
    }
    end = System.currentTimeMillis();
    System.out.println("loading titles: "+(end-start)+"ms.");


    ArrayList<FixedSlidingWindow<Integer>> patternBuffers = new ArrayList<>(N);
    for (int i = 0; i < N; i++) {
      patternBuffers.add(new FixedSlidingWindow<>(i+1));
    }

    Debouncer msg2 = new Debouncer(500);
    StreamingStats hitsPerDoc = new StreamingStats();
    long globalPosition = 0;
    try (PrintWriter hits = IO.openPrintWriter(target.baseDir.childPath("dbpedia.titles.hits.gz"))) {
      int ND = target.corpus.numberOfDocuments();
      for (int docIndex = 0; docIndex < ND; docIndex++) {
        int[] doc = target.corpus.getDocument(docIndex);

        for (int i = 0; i < N; i++) {
          FixedSlidingWindow<Integer> buffer = patternBuffers.get(i);
          buffer.clear();
        }
        int hitcount = 0;
        for (int position = 0; position < doc.length; position++) {
          int term = doc[position];

          for (int i = 0; i < N; i++) {
            FixedSlidingWindow<Integer> buffer = patternBuffers.get(i);
            buffer.add(term);
            if (buffer.full() && matchingBySize.get(i).contains(buffer)) {
              int hitStart = position-i;
              int hitSize = i+1;
              // hit!
              //hits.printf("%d\t%d\t%d\t%s\n", docIndex, position-i, i+1, buffer);
              hits.print(docIndex); hits.print('\t');
              hits.print(hitStart); hits.print('\t');
              hits.print(hitSize); hits.print('\t');

              if(hitSize < 0 || hitStart < 0) {
                System.err.println(Parameters.parseArray(
                    "doc.length", doc.length,
                    "buffer", buffer,
                    "position", position,
                    "buffer.length", buffer.size(),
                    "buffer.hashCode", buffer.hashCode(),
                    "doc[]", Arrays.toString(Arrays.copyOf(doc, Math.max(20, position)))
                ));
              }

              assert(hitStart >= 0);
              assert(hitSize >= 1);

              for (int termIndex = 0; termIndex < hitSize; termIndex++) {
                hits.print(doc[termIndex+hitStart]); hits.print(' ');
              }
              hits.println();

              hitcount++;
            }
          }
        }
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

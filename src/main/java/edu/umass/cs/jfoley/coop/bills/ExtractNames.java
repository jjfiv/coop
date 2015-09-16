package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.FixedSlidingWindow;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author jfoley
 */
public class ExtractNames {
  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("robust.ints"));
    IntCoopIndex index = new IntCoopIndex(new Directory("dbpedia.ints"));

    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();

    System.err.println("Total: " + index.names.size());
    int count = IntMath.fromLong(index.names.size());

    Debouncer msg = new Debouncer(500);
    int N = 20;
    ArrayList<HashSet<List<Integer>>> matchingBySize = new ArrayList<>(N);
    for (int i = 0; i < N; i++) { matchingBySize.add(new HashSet<>()); }

    long start = System.currentTimeMillis();
    TObjectIntHashMap<String> vocabLookup = new TObjectIntHashMap<>(IntMath.fromLong(target.vocab.size()));
    for (Pair<Integer, String> kv : target.vocab.items()) {
      vocabLookup.put(kv.getValue(), kv.getKey());
    }
    long end = System.currentTimeMillis();
    System.err.println("# preload vocab: "+(end-start)+"ms.");
    int docNameIndex = 0;
    for (String name : index.names.values()) {
      docNameIndex++;
      String text = name.replace('_', ' ');
      List<String> query = tokenizer.tokenize(text).terms;
      int size = query.size();
      if(size == 0 || size > N) continue;
      IntList qIds = new IntList(query.size());
      for (String str : query) {
        int tid = vocabLookup.get(str);
        if(tid == vocabLookup.getNoEntryValue()) {
          qIds = null;
          break;
        }
        qIds.push(tid);
      }
      // vocab mismatch; phrase-match therefore not possible
      if(qIds == null) continue;

      matchingBySize.get(size-1).add(qIds);

      if(msg.ready()) {
        System.out.println(text);
        System.out.println(query);
        System.out.println(qIds);
        System.out.println(msg.estimate(docNameIndex, count));
        System.out.println(ListFns.map(matchingBySize, HashSet::size));
      }
    }

    System.out.println(ListFns.map(matchingBySize, HashSet::size));

    try (PrintWriter entities = IO.openPrintWriter(target.baseDir.childPath("dbpedia.titles.intq.gz"))) {
      for (HashSet<List<Integer>> intLists : matchingBySize) {
        for (List<Integer> intList : intLists) {
          for (Integer x : intList) {
            entities.print(x);
            entities.print(' ');
          }
          entities.println();
        }
      }
    }

    ArrayList<FixedSlidingWindow<Integer>> patternBuffers = new ArrayList<>();
    for (int i = 0; i < N; i++) {
      patternBuffers.add(new FixedSlidingWindow<>(i+1));
    }

    Debouncer msg2 = new Debouncer(500);
    StreamingStats hitsPerDoc = new StreamingStats();
    long globalPosition = 0;
    try (PrintWriter hits = IO.openPrintWriter(target.baseDir.childPath("dbpedia.titles.hits.gz"))) {
      int ND = target.corpus.numberOfDocuments();
      for (int docIndex = 133367; docIndex < ND; docIndex++) {
        int[] doc = target.corpus.getDocument(docIndex);

        int hitcount = 0;
        for (int position = 0; position < doc.length; position++) {
          int term = doc[position];

          for (int i = 0; i < patternBuffers.size(); i++) {
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

package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
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
    ArrayList<HashSet<IntList>> matchingBySize = new ArrayList<>(N);
    for (int i = 0; i < N; i++) { matchingBySize.add(new HashSet<>()); }

    long start = System.currentTimeMillis();
    TObjectIntHashMap<String> vocabLookup = new TObjectIntHashMap<>(IntMath.fromLong(target.vocab.size()));
    for (Pair<Integer, String> kv : target.vocab.items()) {
      vocabLookup.put(kv.getValue(), kv.getKey());
    }
    long end = System.currentTimeMillis();
    System.err.println("# preload vocab: "+(end-start)+"ms.");
    int i = 0;
    for (String name : index.names.values()) {
      i++;
      String text = name.replace('_', ' ');
      List<String> query = tokenizer.tokenize(text).terms;
      int size = query.size();
      if(size == 0 || size >= N) continue;
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
        System.out.println(msg.estimate(i, count));
        System.out.println(ListFns.map(matchingBySize, HashSet::size));
      }
    }

    System.out.println(ListFns.map(matchingBySize, HashSet::size));

    try (PrintWriter entities = IO.openPrintWriter(target.baseDir.childPath("dbpedia.titles.intq.gz"))) {
      for (HashSet<IntList> intLists : matchingBySize) {
        for (IntList intList : intLists) {
          for (int ti = 0; ti < intList.size(); ti++) {
            entities.print(intList.getQuick(ti));
            entities.print(' ');
          }
          entities.println();
        }
      }
    }
  }
}

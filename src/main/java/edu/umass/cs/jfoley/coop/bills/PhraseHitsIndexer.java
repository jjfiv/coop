package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.CoderException;
import edu.umass.cs.ciir.waltz.coders.data.ByteBuilder;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import javax.annotation.Nonnull;
import java.io.*;
import java.util.HashMap;

/**
 * @author jfoley
 */
public class PhraseHitsIndexer {
  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    File input = new File(argp.get("input", "/mnt/scratch/jfoley/bills-data/bills2014_phrasehits.start_len.gz"));
    Directory output = Directory.Read(argp.get("output", "bills.ints"));

    IntCoopIndex index = new IntCoopIndex(output);
    TObjectIntHashMap<String> docIds = new TObjectIntHashMap<>();
    for (Pair<Integer, String> kv : index.names.items()) {
      docIds.put(kv.getValue(), kv.getKey());
    }

    PhraseHitsIndexer indexer = new PhraseHitsIndexer();
    int totalExpected = docIds.size();
    int processed = 0;
    int total = 0;
    Debouncer msg = new Debouncer();

    HashMap<IntList, Integer> phraseVocab = new HashMap<>();

    StreamingStats lookupTime = new StreamingStats();
    try (
        PrintWriter hits = IO.openPrintWriter("t_phrase.hits.gz");
        PrintWriter vocab = IO.openPrintWriter("t_phrase.vocab.gz")) {
      try (LinesIterable lines = LinesIterable.fromFile(input)) {
        for (String line : lines) {
          String docName = StrUtil.takeBefore(line, "\t").trim();
          assert (docName.startsWith("bill"));
          docName = docName.substring(4);
          int docId = docIds.get(docName);
          if (docId == docIds.getNoEntryValue()) continue;
          processed++;
          if (msg.ready()) {
            System.out.println(docId + " " + docName);
            System.out.println("rate: " + msg.estimate(processed, totalExpected));
            System.out.println("processed: " + phraseVocab.size()+" unique, "+total+" total docs: "+processed);
            System.out.println("lookup: " + lookupTime);
            System.out.println("term-rate: " + msg.estimate(total));
          }

          String[] items = StrUtil.takeAfter(line, "\t").replace(':', ' ').split(" ");
          total += items.length / 2;
          for (int i = 0; i < items.length; i += 2) {
            int begin = Integer.parseInt(items[i]);
            int width = Integer.parseInt(items[i + 1]);

            // handle TermSlice:
            IntList slice = index.corpus.getSlice(docId, begin, width);
            long start,end;

            int maybeTermId = phraseVocab.size();
            start = System.nanoTime();
            Integer termId = phraseVocab.putIfAbsent(slice, maybeTermId);
            if(termId == null) { termId = maybeTermId; }
            end = System.nanoTime();

            lookupTime.push((end-start) / 1e9);
            if(termId != maybeTermId) {
              vocab.print(termId);
              vocab.print("\t");
              for (int j = 0; j < slice.size(); j++) {
                vocab.print(slice.getQuick(j));
                vocab.print(' ');
              }
              vocab.println();
            }
            hits.println(termId+" "+docId+" "+begin);
          }
        }
      }
    }
  }

}


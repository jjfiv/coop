package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsWriter;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.File;
import java.io.IOException;

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

    int totalExpected = docIds.size();
    int processed = 0;
    int total = 0;
    Debouncer msg = new Debouncer();

    try (
        PhraseHitsWriter writer = new PhraseHitsWriter(output, "patterns")) {
      try (LinesIterable lines = LinesIterable.fromFile(input)) {
        for (String line : lines) {
          String docName = StrUtil.takeBefore(line, "\t").trim();
          assert (docName.startsWith("bill"));
          docName = docName.substring(4);
          int docId = docIds.get(docName);
          processed++;
          if (msg.ready()) {
            System.out.println(docId + " " + docName);
            System.out.println("rate: " + msg.estimate(processed, totalExpected));
            System.out.println("processed: " + writer.vocab.size()+" unique, "+total+" total docs: "+processed);
            System.out.println("term-rate: " + msg.estimate(total));
          }
          if (docId == docIds.getNoEntryValue()) continue;

          String[] items = StrUtil.takeAfter(line, "\t").replace(':', ' ').split(" ");
          total += items.length / 2;
          for (int i = 0; i < items.length; i += 2) {
            int begin = Integer.parseInt(items[i]);
            int width = Integer.parseInt(items[i + 1]);

            // handle TermSlice:
            IntList slice = index.corpus.getSlice(docId, begin, width);
            writer.onPhraseHit(docId, begin, width, slice);
          }
        }
      }
    }
  }

}


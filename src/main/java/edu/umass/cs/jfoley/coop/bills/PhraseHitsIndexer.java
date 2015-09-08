package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
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

    try (
        PrintWriter hits = IO.openPrintWriter("phrase.hits.gz");
        PrintWriter vocab = IO.openPrintWriter("phrase.vocab.gz")) {
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
            System.out.println("term-rate: " + msg.estimate(total));
          }

          String[] items = StrUtil.takeAfter(line, "\t").replace(':', ' ').split(" ");
          total += items.length / 2;
          for (int i = 0; i < items.length; i += 2) {
            int begin = Integer.parseInt(items[i]);
            int width = Integer.parseInt(items[i + 1]);

            // handle TermSlice:
            IntList slice = index.corpus.getSlice(docId, begin, width);
            int termId = phraseVocab.computeIfAbsent(slice, (ignored) -> {
              int id = phraseVocab.size();
              vocab.print(id);
              vocab.print("\t");
              for (int j = 0; j < slice.size(); j++) {
                vocab.print(slice.getQuick(j));
                vocab.print(' ');
              }
              vocab.println();
              return id;
            });
            hits.println(termId+" "+docId+" "+begin);
          }
        }
      }
    }
  }

  public static class ZeroTerminatedIds extends Coder<IntList> {
    @Override
    public boolean knowsOwnSize() {
      return true;
    }

    @Nonnull
    @Override
    public DataChunk writeImpl(IntList obj) throws IOException {
      ByteBuilder bb = new ByteBuilder();
      write(bb.asOutputStream(), obj);
      return bb;
    }

    @Override
    public void write(OutputStream out, IntList obj) throws CoderException {
      for (int x : obj) {
        VarUInt.instance.writePrim(out, x);
      }
      VarUInt.instance.writePrim(out, 0);
    }

    @Nonnull
    @Override
    public IntList readImpl(InputStream inputStream) throws IOException {
      IntList input = new IntList();
      while(true) {
        int x = VarUInt.instance.readPrim(inputStream);
        if(x == 0) break;
        input.push(x);
      }
      return input;
    }
  }
}


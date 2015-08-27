package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.files.FileChannelSource;
import gnu.trove.map.hash.TIntIntHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class IntCorpusAnalysis {

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.create();
    Directory input = Directory.Read(argp.get("input", "robust.ints"));
    FileChannelSource corpus = new FileChannelSource(input.childPath("intCorpus"));
    FileChannelSource docOffsets = new FileChannelSource(input.childPath("docOffset"));

    //long numTerms = corpus.size() / 4;
    long numTerms = 252359881;
    long numDocuments = docOffsets.size() / 8;

    System.out.println("# numTerms: "+numTerms+" numDocs: "+numDocuments);

    int N = 5000;
    List<TIntIntHashMap> freq = new ArrayList<>();
    TIntIntHashMap frequencies = new TIntIntHashMap(N);
    long globalPosition = 0;
    Debouncer msg = new Debouncer(5000);

    for (long docIndex = 0; docIndex < numDocuments; docIndex++) {
      long offset = docOffsets.readLong(docIndex*8);
      long nextOffset = (docIndex+1 == numDocuments) ?
          docOffsets.size() :
          docOffsets.readLong((docIndex+1)*8);

      int docNumber = IntMath.fromLong(docIndex+1);
      int termsInDoc = IntMath.fromLong((nextOffset - offset) / 4);

      for (int termIndex = 0; termIndex < termsInDoc; termIndex++, globalPosition++) {
        int termId = corpus.readInt(offset + (termIndex * 4));
        //System.out.printf("%d:%d: 0x%08x\n", globalPosition, termIndex, termId);
        frequencies.adjustOrPutValue(termId, 1, 1);
      }

      // compact for speed.
      if(frequencies.size() > N) {
        freq.add(frequencies);
        frequencies = new TIntIntHashMap(N);
      }

      if(msg.ready()) {
        System.out.println("Unique Terms: "+frequencies.size());
        System.out.println("Progress: "+globalPosition+" / "+numTerms);
        System.out.println(msg.estimate(globalPosition, numTerms));
        System.out.println();
      }

    }

    TIntIntHashMap finalFrequencies = new TIntIntHashMap(freq.size() * N/2);
    freq.add(frequencies);
    for (TIntIntHashMap compacted : freq) {
      compacted.forEachEntry((key, count) -> {
        finalFrequencies.adjustOrPutValue(key, count, count);
        return true;
      });
    }

    System.out.println(finalFrequencies.size());
    System.out.println(msg.estimate(globalPosition, numTerms));

    try (PrintWriter freqs = IO.openPrintWriter("freqs.tsv.gz")) {
      finalFrequencies.forEachEntry((key, count) -> {
        freqs.println(key+"\t"+count);
        return true;
      });
    }
  }
}

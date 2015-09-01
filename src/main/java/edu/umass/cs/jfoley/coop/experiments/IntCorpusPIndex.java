package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.files.FileChannelSource;
import edu.umass.cs.ciir.waltz.io.postings.ArrayPosList;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.PostingsConfig;
import edu.umass.cs.ciir.waltz.sys.positions.PIndexWriter;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class IntCorpusPIndex {

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory input = Directory.Read(argp.get("input", "bills.ints"));
    FileChannelSource corpus = new FileChannelSource(input.childPath("intCorpus"));
    FileChannelSource docOffsets = new FileChannelSource(input.childPath("docOffset"));

    long numTerms = corpus.size() / 4;
    long numDocuments = docOffsets.size() / 8;

    System.out.println("# numTerms: "+numTerms+" numDocs: "+numDocuments);

    long globalPosition = 0;
    long startTime = System.currentTimeMillis();
    Debouncer msg = new Debouncer(5000);

    String target = "p128";
    PostingsConfig<Integer, PositionsList> cfg = AndQueryPerformance.getCfg(target);

    try (PIndexWriter<Integer> writer = cfg.getWriter(input, target)) {
      for (long docIndex = 0; docIndex < numDocuments; docIndex++) {
        long offset = docOffsets.readLong(docIndex * 8);
        long nextOffset = (docIndex + 1 == numDocuments) ?
            docOffsets.size() :
            docOffsets.readLong((docIndex + 1) * 8);

        int docNumber = IntMath.fromLong(docIndex + 1);
        int termsInDoc = IntMath.fromLong((nextOffset - offset) / 4);

        HashMap<Integer, IntList> withinDocPositions = new HashMap<>();

        for (int termIndex = 0; termIndex < termsInDoc; termIndex++, globalPosition++) {
          int termId = corpus.readInt(offset + (termIndex * 4));
          //System.out.printf("%d:%d: 0x%08x\n", globalPosition, termIndex, termId);
          MapFns.extendCollectionInMap(withinDocPositions, termId, termIndex, (GenerateFn<IntList>) IntList::new);
        }

        for (Map.Entry<Integer, IntList> kv : withinDocPositions.entrySet()) {
          int termId = kv.getKey();
          IntList arr = kv.getValue();
          ArrayPosList pl = new ArrayPosList(arr.unsafeArray(), arr.size());
          writer.add(termId, docNumber, pl);
        }

        if (msg.ready()) {
          System.out.println("Progress: " + globalPosition + " / " + numTerms);
          System.out.println(msg.estimate(globalPosition, numTerms));
          System.out.println();
        }

      }
    }
    long endTime = System.currentTimeMillis();

    System.out.println("Total Time: "+(endTime - startTime));
    // Progress: 249670890 / 252359881
    // 1414862.5 items/s  1.9 seconds left, 98.9% complete.

    // Total Time: 242472
  }
}

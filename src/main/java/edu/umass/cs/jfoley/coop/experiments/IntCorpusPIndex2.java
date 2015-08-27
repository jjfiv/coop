package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.files.FileChannelSource;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.streaming.StreamingPostingBuilder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.postings.positions.SimplePositionsList;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class IntCorpusPIndex2 {

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.create();
    Directory input = Directory.Read(argp.get("input", "robust.ints"));
    FileChannelSource corpus = new FileChannelSource(input.childPath("intCorpus"));
    FileChannelSource docOffsets = new FileChannelSource(input.childPath("docOffset"));

    long numTerms = corpus.size() / 4;
    long numDocuments = docOffsets.size() / 8;

    System.out.println("# numTerms: "+numTerms+" numDocs: "+numDocuments);

    long globalPosition = 0;
    long startTime = System.currentTimeMillis();
    Debouncer msg = new Debouncer(5000);

    try (StreamingPostingBuilder<Integer, PositionsList> writer = new StreamingPostingBuilder<>(FixedSize.ints, new PositionsListCoder(), GalagoIO.getRawIOMapWriter(input.childPath("positions.sort")))) {
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
          SimplePositionsList pl = new SimplePositionsList(kv.getValue());
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

    // With sort super-threading
    // Progress: 251206567 / 252359881
    // 1590539.1 items/s  0.7 seconds left, 99.5% complete.
    // Total Time: 245946

    // Without sort super-threading
    // Progress: 252162765 / 252359881
    // 584866.3 items/s  0.3 seconds left, 99.9% complete.

    // Total Time: 508766
  }
}

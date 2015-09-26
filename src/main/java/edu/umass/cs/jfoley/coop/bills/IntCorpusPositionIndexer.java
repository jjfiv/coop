package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.io.postings.ArrayPosList;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.PostingsConfig;
import edu.umass.cs.ciir.waltz.sys.positions.PIndexWriter;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;
import edu.umass.cs.jfoley.coop.experiments.IndexedSDMQuery;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class IntCorpusPositionIndexer implements Closeable {
  private final PostingsConfig<Integer, PositionsList> cfg;
  private final PIndexWriter<Integer, PositionsList> writer;
  PIndexWriter<Integer, Integer> countsWriter;

  public IntCorpusPositionIndexer(Directory input) throws IOException {
    cfg = new PostingsConfig<>(
        FixedSize.ints,
        new PositionsListCoder(),
        Comparing.defaultComparator(),
        new PositionsCountMetadata()
    );
    this.countsWriter = IndexedSDMQuery.SDMPartReaders.countIndexCfg.getWriter(input, "counts");
    this.writer = cfg.getWriter(input, IntCoopIndex.positionsFileName);
  }

  public void indexFromCorpus(IntVocabBuilder.IntVocabReader reader) {
    try {
      int numDocuments = reader.numberOfDocuments();
      for (int i = 0; i < numDocuments; i++) {
        add(i, reader.getDocument(i));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void add(int documentId, int[] data) {
    HashMap<Integer, IntList> withinDocPositions = new HashMap<>();
    for (int pos = 0; pos < data.length; pos++) {
      MapFns.extendCollectionInMap(withinDocPositions, data[pos], pos, (GenerateFn<IntList>) IntList::new);
    }
    for (Map.Entry<Integer, IntList> kv : withinDocPositions.entrySet()) {
      writer.add(kv.getKey(), documentId, new ArrayPosList(kv.getValue()));
      countsWriter.add(kv.getKey(), documentId, kv.getValue().size());
    }
  }

  @Override
  public void close() throws IOException {
    countsWriter.close();
    writer.close();
  }
}

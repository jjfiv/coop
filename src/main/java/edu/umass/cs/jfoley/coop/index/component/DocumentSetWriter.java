package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.jfoley.coop.index.SmartCollection;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class DocumentSetWriter<K extends Comparable<K>> implements Closeable {
  private final IOMapWriter<K, List<Integer>> ioMapWriter;
  private final HashMap<K, SmartCollection<Integer>> tmpStorage;

  public DocumentSetWriter(IOMapWriter<K, List<Integer>> ioMapWriter) throws IOException {
    this.ioMapWriter = ioMapWriter.getSorting();
    tmpStorage = new HashMap<>();
  }

  public void process(K item, int documentId) throws IOException {
    MapFns.extendCollectionInMap(tmpStorage,
        item,
        documentId,
        (GenerateFn<SmartCollection<Integer>>) () -> new SmartCollection<>(VarUInt.instance));
  }

  @Override
  public void close() throws IOException {
    for (Map.Entry<K, SmartCollection<Integer>> kv : tmpStorage.entrySet()) {
      try (SmartCollection<Integer> docIds = kv.getValue()) {
        ioMapWriter.put(kv.getKey(), docIds.slurp());
      }
    }
    tmpStorage.clear();
    ioMapWriter.close();
  }
}

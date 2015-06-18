package edu.umass.cs.jfoley.coop.index.general;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class DocumentSetWriter<K> implements Closeable {
  private final IOMapWriter<K, List<Integer>> ioMapWriter;
  private final HashMap<K, IntList> tmpStorage;

  public DocumentSetWriter(IOMapWriter<K, List<Integer>> ioMapWriter) throws IOException {
    this.ioMapWriter = ioMapWriter.getSorting();
    tmpStorage = new HashMap<>();
  }

  public void process(K item, int documentId) throws IOException {
    MapFns.extendCollectionInMap(tmpStorage,
        item,
        documentId,
        (GenerateFn<IntList>) IntList::new);
  }

  @Override
  public void close() throws IOException {
    for (Map.Entry<K, IntList> kv : tmpStorage.entrySet()) {
      ioMapWriter.put(kv.getKey(), kv.getValue());
      kv.getValue().clear();
    }
    tmpStorage.clear();
    ioMapWriter.close();
  }
}

package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.SmartDataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.MappingCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For document-level labels.
 * Basically, it's an IOMap where the values are just DocIdSets.
 * @author jfoley
 */
public class DocumentLabelIndex {
  public static class SmartDocIdSet implements Closeable {
    public final SmartDataChunk storage;
    private final VarUInt intCoder;
    private int count;

    public SmartDocIdSet() {
      this.storage = new SmartDataChunk();
      this.intCoder = VarUInt.instance;
      this.count = 0;
    }

    public void add(int docId) {
      storage.add(intCoder, docId);
    }

    public InputStream getInputStream() throws IOException {
      storage.flush();
      return storage.asInputStream();
    }

    public IntList slurp() throws IOException {
      IntList memory = new IntList();
      memory.resize(count);
      try(InputStream stream = getInputStream()) {
        for (int i = 0; i < count; i++) {
          memory.add(intCoder.readImpl(stream));
        }
      }
      return memory;
    }

    @Override
    public void close() throws IOException {
      storage.close();
    }
  }

  public static class NamespacedLabel implements Comparable<NamespacedLabel> {
    private final String fullString;

    public NamespacedLabel(String namespace, String label) {
      this.fullString = namespace+":"+label;
    }
    @Override
    public String toString() {
      return fullString;
    }
    public static NamespacedLabel fromString(String x) {
      return new NamespacedLabel(
          StrUtil.takeBefore(x, ":"),
          StrUtil.takeAfter(x, ":"));
    }
    public static Coder<NamespacedLabel> coder = new MappingCoder<>(
        CharsetCoders.utf8Raw,
        NamespacedLabel::toString,
        NamespacedLabel::fromString
    );

    @Override
    public int compareTo(NamespacedLabel o) {
      return toString().compareTo(o.toString());
    }
  }

  public static class Writer implements Closeable {
    private final IOMapWriter<NamespacedLabel, List<Integer>> ioMapWriter;
    private final HashMap<NamespacedLabel, SmartDocIdSet> tmpStorage;

    public Writer(IOMapWriter<NamespacedLabel,List<Integer>> ioMapWriter) throws IOException {
      this.tmpStorage = new HashMap<>();
      this.ioMapWriter = ioMapWriter.getSorting();
    }

    public void add(String namespace, String label, int docId) {
      NamespacedLabel key = new NamespacedLabel(namespace, label);
      SmartDocIdSet forLabel = tmpStorage.get(key);
      if(forLabel == null) {
        forLabel = new SmartDocIdSet();
        tmpStorage.put(key, forLabel);
      }
      forLabel.add(docId);
    }

    @Override
    public void close() throws IOException {
      for (Map.Entry<NamespacedLabel, SmartDocIdSet> kv : tmpStorage.entrySet()) {
        try (SmartDocIdSet idSet = kv.getValue()) {
          ioMapWriter.put(kv.getKey(), idSet.slurp());
        } // and delete any files used for that IdSet as we go.
      }
      tmpStorage.clear();
      ioMapWriter.close();
    }
  }


  public static class Reader implements Closeable {
    private final IOMap<NamespacedLabel, List<Integer>> ioMap;
    public Reader(String path) throws IOException {
      this.ioMap = GalagoIO.openIOMap(NamespacedLabel.coder, new DeltaIntListCoder(), path);
    }

    public List<Integer> getMatchingDocs(String field, String label) throws IOException {
      NamespacedLabel key = new NamespacedLabel(field, label);
      return ioMap.get(key);
    }

    @Override
    public void close() throws IOException {
      ioMap.close();
    }
  }
}

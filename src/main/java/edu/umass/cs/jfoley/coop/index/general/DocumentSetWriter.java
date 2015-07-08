package edu.umass.cs.jfoley.coop.index.general;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.BufferList;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.data.SmartDataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.RawIOMapWriter;
import edu.umass.cs.ciir.waltz.coders.reduce.Reducer;
import edu.umass.cs.ciir.waltz.coders.sorter.ExternalSortingWriter;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author jfoley
 */
public class DocumentSetWriter<K> implements Closeable {
  private final RawIOMapWriter writer;
  private final TemporaryDirectory tmpdir;
  private final ExternalSortingWriter<DocumentSetChunk<K>> sorter;
  private final Coder<K> keyCoder;

  public static class DocumentSetChunk<K> implements Comparable<DocumentSetChunk<K>> {
    public K key;
    public IntList docs;
    Comparator<K> cmp;

    public DocumentSetChunk(K key, int doc) {
      this.key = key;
      this.docs = new IntList();
      this.docs.add(doc);
      this.cmp = Comparing.defaultComparator();
    }

    public DocumentSetChunk(K key, IntList docs) {
      this.key = key;
      this.docs = docs;
    }

    @Override
    public int compareTo(@Nonnull DocumentSetChunk<K> o) {
      int cv = cmp.compare(this.key, o.key);
      if(cv != 0) return cv;
      return Integer.compare(this.docs.get(0), o.docs.get(0));
    }
  }

  public static class DocumentSetChunkReducer<K> extends Reducer<DocumentSetChunk<K>> {
    @Override
    public boolean shouldMerge(DocumentSetChunk<K> lhs, DocumentSetChunk<K> rhs) {
      return lhs.docs.size() < 128 && Objects.equals(lhs.key, rhs.key);
    }

    @Override
    public DocumentSetChunk<K> merge(DocumentSetChunk<K> lhs, DocumentSetChunk<K> rhs) {
      lhs.docs.addAll(rhs.docs);
      return lhs;
    }
  }

  public static class DocumentSetChunkCoder<K> extends Coder<DocumentSetChunk<K>> {
    public final Coder<K> keyCoder;
    public final Coder<List<Integer>> listCoder;

    public DocumentSetChunkCoder(Coder<K> keyCoder) {
      this(keyCoder, new DeltaIntListCoder());
    }
    public DocumentSetChunkCoder(Coder<K> keyCoder, Coder<List<Integer>> listCoder) {
      this.keyCoder = keyCoder.lengthSafe();
      this.listCoder = listCoder.lengthSafe();
    }

    @Override
    public boolean knowsOwnSize() {
      return true;
    }

    @Nonnull
    @Override
    public DataChunk writeImpl(DocumentSetChunk<K> obj) throws IOException {
      BufferList output = new BufferList();
      output.add(keyCoder, obj.key);
      output.add(listCoder, obj.docs);
      return output.compact();
    }

    @Nonnull
    @Override
    public DocumentSetChunk<K> readImpl(InputStream inputStream) throws IOException {
      K key = keyCoder.readImpl(inputStream);
      IntList docs = new IntList();
      docs.addAll(listCoder.readImpl(inputStream));
      return new DocumentSetChunk<>(key, docs);
    }
  }

  public DocumentSetWriter(Coder<K> keyCoder, RawIOMapWriter rawWriter) throws IOException {
    this.keyCoder = keyCoder;
    this.tmpdir = new TemporaryDirectory();
    this.sorter = new ExternalSortingWriter<>(
        tmpdir.get(),
        new DocumentSetChunkReducer<>(),
        new DocumentSetChunkCoder<>(keyCoder));
    this.writer = rawWriter.getSorting();
  }

  public void process(K item, int documentId) throws IOException {
    sorter.process(new DocumentSetChunk<>(item, documentId));
  }

  @Override
  public void close() throws IOException {
    sorter.close();

    K prevKey = null;
    SmartDataChunk value = null;
    int previous = 0;
    int total = 0;
    for (DocumentSetChunk<K> current : sorter.getOutput()) {
      if(prevKey == null || !Objects.equals(current.key, prevKey)) {
        // flush() {
        if(prevKey != null) {
          BufferList prefixCount = new BufferList();
          prefixCount.add(VarUInt.instance, total);
          value.flush();
          prefixCount.add(value);
          writer.put(keyCoder.writeData(prevKey), prefixCount);
        }

        previous = 0;
        total = 0;
        prevKey = current.key;
        value = new SmartDataChunk();
      }

      total += current.docs.size();
      for (int doc : current.docs) {
        int delta = doc - previous;
        value.add(VarUInt.instance, delta);
        previous = doc;
      }
    }
    // flush
    if(prevKey != null) {
      BufferList prefixCount = new BufferList();
      prefixCount.add(VarUInt.instance, total);
      value.flush();
      prefixCount.add(value);
      writer.put(keyCoder.writeData(prevKey), prefixCount);
    }

    writer.close(); // close writer
    tmpdir.close(); // delete files
  }
}

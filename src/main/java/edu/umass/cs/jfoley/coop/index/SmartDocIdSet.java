package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.coders.data.SmartDataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Right now, this class is not order dependent, but it could be for space and maybe time efficiency.
 * @author jfoley
 */
public class SmartDocIdSet implements Closeable {
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
    count++;
  }

  public InputStream getInputStream() throws IOException {
    storage.flush();
    return storage.asInputStream();
  }

  public IntList slurp() throws IOException {
    IntList memory = new IntList();
    memory.resize(count);
    storage.flush();
    try (InputStream stream = getInputStream()) {
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

  public int size() {
    return count;
  }
}

package edu.umass.cs.jfoley.coop.index.general;

import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.SmartDataChunk;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Stores a collection on disk when the JVM tells us we're running out of memory.
 * You can {@code slurp()} it into memory when you're sure you need all of it, and {@code close()} it to delete any temporary files. <strong>ANY</strong> writing while reading might invalidate your {@link Iterator} or {@link InputStream}.
 * @author jfoley
 */
public class SmartCollection<T> extends AbstractCollection<T> implements Closeable {
  public final SmartDataChunk storage;
  private final Coder<T> itemCoder;
  private int count;

  public SmartCollection(@Nonnull Coder<T> itemCoder) {
    this.storage = new SmartDataChunk();
    this.itemCoder = Objects.requireNonNull(itemCoder.lengthSafe());
    this.count = 0;
  }

  public boolean add(@Nonnull T val) {
    storage.add(itemCoder, val);
    count++;
    return true;
  }

  public InputStream getInputStream() throws IOException {
    storage.flush();
    return storage.asInputStream();
  }

  /**
   * Generally, you want to call close() after you do this.
   * @return the contents of this collection as a memory-materialized list.
   * @throws IOException if flushing/reading goes poorly.
   */
  public List<T> slurp() throws IOException {
    List<T> memory = new ArrayList<>(count);
    storage.flush();
    try (InputStream stream = getInputStream()) {
      for (int i = 0; i < count; i++) {
        memory.add(itemCoder.read(stream));
      }
    }
    return memory;
  }

  @Nonnull
  @Override
  public Iterator<T> iterator() {
    try {
      storage.flush();
      final int iterCount = count;
      final InputStream is = getInputStream();
      return new Iterator<T>() {
        private int i = 0;
        @Override
        public boolean hasNext() {
          return i < iterCount;
        }

        @Override
        @Nonnull
        public T next() {
          if(!hasNext()) throw new NoSuchElementException();
          return itemCoder.read(is);
        }
      };
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws IOException {
    storage.close();
  }

  @Override
  public int size() {
    return count;
  }
}

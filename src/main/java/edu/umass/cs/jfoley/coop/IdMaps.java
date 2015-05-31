package edu.umass.cs.jfoley.coop;

import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * @author jfoley.
 */
public class IdMaps {
  public static class Writer<V extends Comparable<V>> implements Flushable, Closeable {
    public final IOMapWriter<Integer, V> forwardWriter;
    public final IOMapWriter<V, Integer> reverseWriter;

    public Writer(IOMapWriter<Integer, V> forwardWriter, IOMapWriter<V, Integer> reverseWriter) throws IOException {
      this.forwardWriter = forwardWriter.getSorting();
      this.reverseWriter = reverseWriter.getSorting();
    }

    public void put(int id, V value) throws IOException {
      forwardWriter.put(id, value);
      reverseWriter.put(value, id);
    }

    @Override
    public void close() throws IOException {
      forwardWriter.close();
      reverseWriter.close();
    }

    @Override
    public void flush() throws IOException {
      forwardWriter.flush();
      reverseWriter.flush();
    }

  }
  public static <V extends Comparable<V>> Writer<V> openWriter(String baseName, Coder<Integer> keyCoder, Coder<V> valCoder) throws IOException {
    return new Writer<>(
        GalagoIO.getIOMapWriter(keyCoder, valCoder, baseName + ".fwd"),
        GalagoIO.getIOMapWriter(valCoder, keyCoder, baseName+".rev")
    );
  }
  public static <V extends Comparable<V>> Reader<V> openReader(String baseName, Coder<Integer> keyCoder, Coder<V> valCoder) throws IOException {
    return new Reader<>(
        GalagoIO.openIOMap(keyCoder, valCoder, baseName + ".fwd"),
        GalagoIO.openIOMap(valCoder, keyCoder, baseName+".rev")
    );
  }

  public static class Reader<V extends Comparable<V>> implements Closeable {
    public final IOMap<Integer, V> forwardReader;
    public final IOMap<V, Integer> reverseReader;

    public Reader(IOMap<Integer, V> forwardReader, IOMap<V, Integer> reverseReader) {
      this.forwardReader = forwardReader;
      this.reverseReader = reverseReader;
    }

    @Override
    public void close() throws IOException {
      this.forwardReader.close();
      this.reverseReader.close();
    }
  }

}

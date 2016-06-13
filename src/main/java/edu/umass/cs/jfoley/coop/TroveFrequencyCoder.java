package edu.umass.cs.jfoley.coop;

import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.lucene.util.BytesRef;

import java.io.*;

/**
 * @author jfoley
 */
public class TroveFrequencyCoder {
  public static BytesRef save(TObjectIntHashMap<String> facets) {
    return toBytes(facets);
  }

  @SuppressWarnings("unchecked")
  public static TObjectIntHashMap<String> read(BytesRef bytesRef) {
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytesRef.bytes, bytesRef.offset, bytesRef.length))) {
      return (TObjectIntHashMap<String>) ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> BytesRef toBytes(T object) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
      oos.writeObject(object);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new BytesRef(out.toByteArray());
  }
}

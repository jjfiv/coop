package edu.umass.cs.jfoley.coop.coders;

import ciir.jfoley.chai.lang.ThreadsafeLazyPtr;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.ByteArray;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author jfoley
 */
public class KryoCoder<T> extends Coder<T> {
  public static ThreadsafeLazyPtr<Kryo> kryo = new ThreadsafeLazyPtr<>(Kryo::new);

  public final Class<T> encodingClass;

  public KryoCoder(Class<T> encodingClass) {
    this.encodingClass = encodingClass;
  }

  @Override
  public boolean knowsOwnSize() {
    return true;
  }

  @Nonnull
  @Override
  public DataChunk writeImpl(T obj) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Output output = new Output(baos);
    kryo.get().writeObject(output, obj);
    output.close();
    return new ByteArray(baos.toByteArray());
  }

  @Nonnull
  @Override
  public T readImpl(InputStream inputStream) throws IOException {
    Input input = new Input(inputStream);
    return kryo.get().readObject(input, encodingClass);
  }
}

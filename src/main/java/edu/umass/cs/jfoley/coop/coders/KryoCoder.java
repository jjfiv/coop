package edu.umass.cs.jfoley.coop.coders;

import ciir.jfoley.chai.io.StreamFns;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.CoderException;
import edu.umass.cs.ciir.waltz.coders.data.ByteArray;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Ugh, because Kryo's Input class is super-greedy and expects to own the whole stream, we length-prefix whatever they encode in order to do our own processing.
 * @author jfoley
 */
public class KryoCoder<T> extends Coder<T> {
  // Ditch the lock.
  public static ThreadLocal<Kryo> kryo = new ThreadLocal<Kryo>() {
    @Override public Kryo initialValue() { return new Kryo(); }
  };

  public final Class<T> encodingClass;

  public KryoCoder(Class<T> encodingClass) {
    this.encodingClass = encodingClass;
  }

  @Override
  public boolean knowsOwnSize() {
    return true;
  }

  // 4k
  public static final int StartBufferSize = 4096;
  // 64M
  public static final int MaxBufferSize = 64*1024*1024;

  @Nonnull
  @Override
  public DataChunk writeImpl(T obj) throws IOException {
    Output output = new Output(StartBufferSize, MaxBufferSize);
    kryo.get().writeObjectOrNull(output, obj, encodingClass);

    ByteArrayOutputStream total = new ByteArrayOutputStream();
    VarUInt.instance.write(total, output.position());
    total.write(output.getBuffer(), 0, output.position());
    return new ByteArray(total.toByteArray());
  }

  @Override
  public void write(OutputStream out, T elem) {
    try {
      Output output = new Output(StartBufferSize, MaxBufferSize);
      kryo.get().writeObjectOrNull(output, elem, encodingClass);

      // short-cut for efficiency
      VarUInt.instance.write(out, output.position());
      out.write(output.getBuffer(), 0, output.position());
    } catch (IOException | KryoException e) {
      throw new CoderException(e, this.getClass());
    }
  }

  @Nonnull
  @Override
  public T readImpl(InputStream inputStream) throws IOException {
    try {
      int kryoSize = VarUInt.instance.read(inputStream);
      Input input = new Input(StreamFns.readBytes(inputStream, kryoSize));
      return kryo.get().readObject(input, encodingClass);
    } catch (KryoException kryoError) {
      throw new CoderException(kryoError, KryoCoder.class);
    }
  }
}

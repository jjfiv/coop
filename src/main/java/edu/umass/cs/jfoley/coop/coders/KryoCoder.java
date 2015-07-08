package edu.umass.cs.jfoley.coop.coders;

import ciir.jfoley.chai.io.StreamFns;
import ciir.jfoley.chai.lang.ThreadsafeLazyPtr;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.CoderException;
import edu.umass.cs.ciir.waltz.coders.data.BufferList;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Ugh, because Kryo's Input class is super-greedy and expects to own the whole stream, we length-prefix whatever they encode in order to do our own processing.
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
    kryo.get().writeObjectOrNull(output, obj, encodingClass);
    output.close();

    byte[] kryod = baos.toByteArray();
    BufferList bl = new BufferList();
    bl.add(VarUInt.instance, kryod.length);
    bl.add(kryod);
    return bl;
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

package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.StreamFns;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.ByteBuilder;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.streams.StaticStream;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author jfoley
 */
public class PhraseHitListCoder extends Coder<PhraseHitList> {

  @Nonnull
  @Override
  public Class<?> getTargetClass() {
    return PhraseHitList.class;
  }

  @Override
  public boolean knowsOwnSize() {
    return true;
  }

  @Nonnull
  @Override
  public DataChunk writeImpl(PhraseHitList obj) throws IOException {
    ByteBuilder bb = new ByteBuilder();
    this.write(bb.asOutputStream(), obj);
    return bb;
  }

  @Override
  public void write(OutputStream out, PhraseHitList obj) {
    IntList data = obj.memData;

    VarUInt.instance.writePrim(out, obj.size());

    for (int i = 0; i < data.size(); i += 3) {
      int start = data.getQuick(i);
      int size = data.getQuick(i + 1);
      int id = data.getQuick(i + 2);

      VarUInt.instance.writePrim(out, start);
      VarUInt.instance.writePrim(out, size);
      FixedSize.ints.write(out, id);
    }
  }

  @Nonnull
  @Override
  public PhraseHitList readImpl(InputStream inputStream) throws IOException {
    int count = VarUInt.instance.readPrim(inputStream);
    PhraseHitList out = new PhraseHitList(count);
    for (int i = 0; i < count; i++) {
      //System.err.println("read["+i+"]: "+delta);
      int start = VarUInt.instance.readPrim(inputStream);
      int size = VarUInt.instance.readPrim(inputStream);
      int id = FixedSize.ints.read(inputStream);
      //System.err.println("read["+i+"]: "+new PhraseHit(delta, size, id));
      out.add(start, size, id);
    }
    return out;
  }

  /** Reading of something that can be read again. */
  @Nonnull
  public PhraseHitList read(StaticStream streamFn) throws IOException {
    long size = streamFn.length();

    long start = System.nanoTime();
    byte[] data = StreamFns.readBytes(streamFn.getNewStream(), (int) size);
    long end = System.nanoTime();
    long slurpTime = (end-start);

    start = System.nanoTime();
    PhraseHitList x = readImpl(new ByteArrayInputStream(data));
    end = System.nanoTime();

    long parseTime = (end-start);
    System.err.println(size+","+ parseTime +","+slurpTime);
    return x;
  }
}

package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.ByteBuilder;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;

import javax.annotation.Nonnull;
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
    int prevStart = 0;

    VarUInt.instance.writePrim(out, obj.size());

    for (int i = 0; i < data.size(); i += 3) {
      int start = data.getQuick(i);
      int size = data.getQuick(i + 1);
      int id = data.getQuick(i + 2);
      System.err.println("write["+i+"]: "+new PhraseHit(start, size, id));

      int delta = start - prevStart;
      VarUInt.instance.writePrim(out, delta);
      VarUInt.instance.writePrim(out, size);
      FixedSize.ints.write(out, id);

      prevStart = start;
    }
  }

  @Nonnull
  @Override
  public PhraseHitList readImpl(InputStream inputStream) throws IOException {
    int count = VarUInt.instance.readPrim(inputStream);
    PhraseHitList out = new PhraseHitList(count);
    int delta = 0;
    for (int i = 0; i < count; i++) {
      delta += VarUInt.instance.readPrim(inputStream);
      int size = VarUInt.instance.readPrim(inputStream);
      int id = FixedSize.ints.read(inputStream);
      System.err.println(new PhraseHit(delta, size, id));
      out.add(delta, size, id);
    }
    return out;
  }
}

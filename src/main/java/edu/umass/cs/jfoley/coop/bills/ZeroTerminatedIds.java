package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.CoderException;
import edu.umass.cs.ciir.waltz.coders.data.ByteBuilder;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author jfoley
 */
public class ZeroTerminatedIds extends Coder<IntList> {
  @Nonnull
  @Override
  public Class<?> getTargetClass() {
    return IntList.class;
  }

  @Override
  public boolean knowsOwnSize() {
    return true;
  }

  @Nonnull
  @Override
  public DataChunk writeImpl(IntList obj) throws IOException {
    ByteBuilder bb = new ByteBuilder();
    write(bb.asOutputStream(), obj);
    return bb;
  }

  @Override
  public void write(OutputStream out, IntList obj) throws CoderException {
    for (int x : obj) {
      VarUInt.instance.writePrim(out, x);
    }
    VarUInt.instance.writePrim(out, 0);
  }

  @Nonnull
  @Override
  public IntList readImpl(InputStream inputStream) throws IOException {
    IntList input = new IntList();
    while (true) {
      int x = VarUInt.instance.readPrim(inputStream);
      if (x == 0) break;
      input.push(x);
    }
    return input;
  }
}

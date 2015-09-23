package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.CoderException;
import edu.umass.cs.ciir.waltz.coders.data.ByteBuilder;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;

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
    try {
      out.write(obj.memData.encode());
    } catch (IOException e) {
      throw new CoderException(e, this.getClass());
    }
  }

  @Nonnull
  @Override
  public PhraseHitList readImpl(InputStream inputStream) throws IOException {
    return new PhraseHitList(IntList.decode(inputStream));
  }
}

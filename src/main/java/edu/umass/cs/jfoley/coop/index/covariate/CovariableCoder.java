package edu.umass.cs.jfoley.coop.index.covariate;

import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.BufferList;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

/**
 * An encoder for a Covariable.
 * @author jfoley
 */
public class CovariableCoder<A, B> extends Coder<Covariable<A, B>> {
  private Coder<A> xCoder;
  private Coder<B> yCoder;

  public CovariableCoder(Coder<A> xCoder, Coder<B> yCoder) {
    this.xCoder = xCoder;
    this.yCoder = yCoder;
  }

  @Override
  public boolean knowsOwnSize() {
    return true;
  }

  @Nonnull
  @Override
  public DataChunk writeImpl(Covariable<A, B> obj) throws IOException {
    BufferList bl = new BufferList();
    bl.add(xCoder, obj.left);
    bl.add(yCoder, obj.right);
    return bl.compact();
  }

  @Nonnull
  @Override
  public Covariable<A, B> readImpl(InputStream inputStream) throws IOException {
    A left = xCoder.readImpl(inputStream);
    B right = yCoder.readImpl(inputStream);
    return new Covariable<>(left, right);
  }
}

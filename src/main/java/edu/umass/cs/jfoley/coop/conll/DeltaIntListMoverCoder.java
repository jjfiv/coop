package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.BufferList;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.streams.SkipInputStream;
import edu.umass.cs.ciir.waltz.coders.streams.StaticStream;
import edu.umass.cs.ciir.waltz.dociter.KeyBlock;
import edu.umass.cs.ciir.waltz.dociter.movement.AMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author jfoley
 */
public class DeltaIntListMoverCoder extends Coder<Mover> {
  Coder<Integer> itemCoder = VarUInt.instance;
  Coder<Integer> countCoder = VarUInt.instance;

  @Override
  public boolean knowsOwnSize() {
    return true;
  }

  @Nonnull
  @Override
  public DataChunk writeImpl(Mover obj) throws IOException {
    BufferList bl = new BufferList();
    int total = obj.totalKeys();
    bl.add(countCoder, total);
    int prev = 0;

    for (; !obj.isDone(); obj.nextBlock()) {
      BufferList block = new BufferList();
      for (; !obj.isDoneWithBlock(); obj.nextKey()) {
        int x = obj.currentKey();
        int delta = x - prev;
        bl.add(itemCoder, delta);
        prev = x;
      }
      bl.add(block.compact());
    }
    return bl;
  }

  public static class DeltaIntListStreamMover extends AMover {
    final StaticStream streamFn;
    InputStream stream;
    int total = 0;
    int currentIndex = 0;
    int previousValue = 0;
    Coder<Integer> itemCoder = VarUInt.instance;
    Coder<Integer> countCoder = VarUInt.instance;

    public DeltaIntListStreamMover(StaticStream streamFn) {
      this.streamFn = streamFn;
      reset();
    }

    @Override
    public void nextBlock() {
      this.currentBlock = null;
      this.index = 0;

      IntList block = new IntList();
      int end = Math.min(total, currentIndex + 128);
      try {
        for (; currentIndex < end; currentIndex++) {
          previousValue += itemCoder.read(stream);
          block.add(previousValue);
        }
        if (block.isEmpty()) {
          return;
        }
        currentBlock = new KeyBlock(block);
      } catch (Exception e) {
        System.out.printf("total: %d, end: %d\n", total, end);
        throw e;
      }
    }

    @Override
    public void reset() {
      stream = streamFn.getNewStream();
      total = countCoder.read(stream);
      currentIndex = 0;
      previousValue = 0;
      nextBlock();
    }

    @Override
    public int totalKeys() {
      return total;
    }
  }

  @Nonnull
  @Override
  public Mover read(StaticStream streamFn) throws IOException {
    return new DeltaIntListStreamMover(streamFn);
  }

  @Nonnull
  @Override
  public Mover readImpl(InputStream inputStream) throws IOException {
    return read(new StaticStream() {
      @Override
      public SkipInputStream getNewStream() {
        return SkipInputStream.wrap(inputStream);
      }

      @Override
      public long length() {
        throw new UnsupportedOperationException();
      }
    });
  }
}

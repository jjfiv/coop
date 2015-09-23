package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.TemporaryFile;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class PhraseHitListTest {

  @Test
  public void testFind() {
    PhraseHitList db = new PhraseHitList();
    db.add(0, 1, 3);
    db.add(0, 7, 1);
    db.add(10, 14, 2);

    assertEquals((List) Collections.emptyList(), db.find(7, 1));
    assertEquals((List) Collections.singletonList(1), db.find(6,1));
  }

  @Test
  public void testSerialize() throws IOException {
    int N = 70000;
    Random rand = new Random(13);
    PhraseHitList x = new PhraseHitList(N);

    long start, end;

    start = System.currentTimeMillis();
    for (int i = 0; i < N; i++) {
      int begin = Math.abs(rand.nextInt(20000));
      int size = Math.abs(rand.nextInt(1000));
      int eid = Math.abs(rand.nextInt());
      x.add(begin, size, eid);
    }
    end = System.currentTimeMillis();
    System.out.println("# Random init time: "+(end-start)+"ms.");

    PhraseHitListCoder coder = new PhraseHitListCoder();

    try (TemporaryFile tmp = new TemporaryFile(".bin")) {
      try (OutputStream out = IO.openOutputStream(tmp.get())) {
        start = System.currentTimeMillis();
        coder.write(out, x);
      }
      end = System.currentTimeMillis();
      System.out.println("# Write time: "+(end-start)+"ms.");
      try (InputStream in = IO.openInputStream(tmp.get())) {
        start = System.currentTimeMillis();
        PhraseHitList y = coder.read(in);
        end = System.currentTimeMillis();
        assertEquals(x, y);
        System.out.println("# Read time: "+(end-start)+"ms.");
      }
    }

    try (TemporaryFile tmp = new TemporaryFile(".bin.lzf")) {
      try (OutputStream out = IO.openOutputStream(tmp.get())) {
        start = System.currentTimeMillis();
        coder.write(out, x);
      }
      end = System.currentTimeMillis();
      System.out.println("# LZF Write time: "+(end-start)+"ms.");
      try (InputStream in = new BufferedInputStream(IO.openInputStream(tmp.get()))) {
        start = System.currentTimeMillis();
        PhraseHitList y = coder.read(in);
        end = System.currentTimeMillis();
        assertEquals(x, y);
        System.out.println("# LZF Read time: " + (end - start) + "ms.");
      }
    }

    try (TemporaryFile tmp = new TemporaryFile(".bin.lzf")) {
      start = System.currentTimeMillis();
      try (OutputStream out = IO.openOutputStream(tmp.get())) {
        IntList raw = x.memData;
        out.write(raw.encode());
      }
      end = System.currentTimeMillis();
      System.out.println("# Raw-LZF Write time: "+(end-start)+"ms.");

      try (InputStream in = new BufferedInputStream(IO.openInputStream(tmp.get()))) {
        start = System.currentTimeMillis();
        IntList raw = IntList.decode(in);
        PhraseHitList y = new PhraseHitList(raw);
        end = System.currentTimeMillis();

        assertEquals(x, y);
        System.out.println("# Raw-LZF Read time: " + (end - start) + "ms.");
      }
    }

    System.out.println("# Done with testing.");

  }

}
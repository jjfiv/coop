package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.TemporaryFile;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.compress.BZipCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.compress.GZipCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.compress.LZFCoder;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
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
    int N = 15000;
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

    Coder<PhraseHitList> coder = new PhraseHitListCoder();

    try (TemporaryFile tmp = new TemporaryFile(".bin")) {
      try (OutputStream out = IO.openOutputStream(tmp.get())) {
        start = System.currentTimeMillis();
        coder.write(out, x);
      }
      end = System.currentTimeMillis();
      System.err.println("size: "+Files.size(tmp.get().toPath()));
      System.out.println("# Write time: "+(end-start)+"ms.");
      try (InputStream in = IO.openInputStream(tmp.get())) {
        start = System.currentTimeMillis();
        PhraseHitList y = coder.read(in);
        end = System.currentTimeMillis();
        assertEquals(x, y);
        System.out.println("# Read time: "+(end-start)+"ms.");
      }
    }

    coder = new GZipCoder<>(new PhraseHitListCoder());

    try (TemporaryFile tmp = new TemporaryFile(".bin")) {
      try (OutputStream out = IO.openOutputStream(tmp.get())) {
        start = System.currentTimeMillis();
        coder.write(out, x);
      }
      System.err.println("size: "+Files.size(tmp.get().toPath()));
      end = System.currentTimeMillis();
      System.out.println("# GZip Write time: "+(end-start)+"ms.");
      try (InputStream in = IO.openInputStream(tmp.get())) {
        start = System.currentTimeMillis();
        PhraseHitList y = coder.read(in);
        end = System.currentTimeMillis();
        assertEquals(x, y);
        System.out.println("# GZip Read time: " + (end - start) + "ms.");
      }
    }

    coder = new LZFCoder<>(new PhraseHitListCoder());

    try (TemporaryFile tmp = new TemporaryFile(".bin")) {
      try (OutputStream out = IO.openOutputStream(tmp.get())) {
        start = System.currentTimeMillis();
        coder.write(out, x);
      }
      System.err.println("size: "+Files.size(tmp.get().toPath()));
      end = System.currentTimeMillis();
      System.out.println("# LZF Write time: "+(end-start)+"ms.");
      try (InputStream in = IO.openInputStream(tmp.get())) {
        start = System.currentTimeMillis();
        PhraseHitList y = coder.read(in);
        end = System.currentTimeMillis();
        assertEquals(x, y);
        System.out.println("# LZF Read time: " + (end - start) + "ms.");
      }
    }

    coder = new BZipCoder<>(new PhraseHitListCoder());

    try (TemporaryFile tmp = new TemporaryFile(".bin")) {
      try (OutputStream out = IO.openOutputStream(tmp.get())) {
        start = System.currentTimeMillis();
        coder.write(out, x);
      }
      System.err.println("size: "+Files.size(tmp.get().toPath()));
      end = System.currentTimeMillis();
      System.out.println("# BZip2 Write time: "+(end-start)+"ms.");
      try (InputStream in = IO.openInputStream(tmp.get())) {
        start = System.currentTimeMillis();
        PhraseHitList y = coder.read(in);
        end = System.currentTimeMillis();
        assertEquals(x, y);
        System.out.println("# BZip2 Read time: " + (end - start) + "ms.");
      }
    }

  }

}
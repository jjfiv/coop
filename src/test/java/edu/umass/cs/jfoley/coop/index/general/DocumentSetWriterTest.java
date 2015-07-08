package edu.umass.cs.jfoley.coop.index.general;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.TemporaryDirectory;
import ciir.jfoley.chai.random.Sample;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.conll.DeltaIntListMoverCoder;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class DocumentSetWriterTest {
  @Test
  public void simpleTest() throws IOException {
    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (DocumentSetWriter<String> labels = new DocumentSetWriter<>(CharsetCoders.utf8, GalagoIO.getRawIOMapWriter(tmpdir.childPath("labels")))) {
        labels.process("blarg", 1);
        labels.process("blarg", 3);
        labels.process("asdf", 1);
        labels.process("asdf", 2);
        labels.process("asdf", 3);
      }

      try (IOMap<String, List<Integer>> dsr = GalagoIO.openIOMap(CharsetCoders.utf8, new DeltaIntListCoder(), tmpdir.childPath("labels"))) {
        assertEquals(Arrays.asList(1,2,3), dsr.get("asdf"));
        assertEquals(Arrays.asList(1, 3), dsr.get("blarg"));
      }

      try (IOMap<String, Mover> dsr = GalagoIO.openIOMap(CharsetCoders.utf8, new DeltaIntListMoverCoder(), tmpdir.childPath("labels"))) {
        IntList asdf = new IntList();
        dsr.get("asdf").execute(asdf::add);
        assertEquals(Arrays.asList(1,2,3), asdf);

        IntList blarg = new IntList();
        dsr.get("blarg").execute(blarg::add);
        assertEquals(Arrays.asList(1, 3), blarg);
      }
    }
  }

  @Test
  public void largeNoisyTest() throws IOException {
    IntList data1 = new IntList();
    String key = "key";

    // collect reasonably large docid set:
    IterableFns.intoSink(
        IterableFns.map(
            IterableFns.sortedStreamingGroupBy(
                IterableFns.sorted(Sample.randomIntegers(100000, 3000000)),
                Objects::equals),
            (lst) -> lst.get(0)),
        data1::add);

    Collections.shuffle(data1);

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (DocumentSetWriter<String> labels = new DocumentSetWriter<>(CharsetCoders.utf8, GalagoIO.getRawIOMapWriter(tmpdir.childPath("labels")))) {
        for (int doc : data1) {
          labels.process("key", doc);
        }
      }

      Collections.sort(data1);
      try (IOMap<String, List<Integer>> dsr = GalagoIO.openIOMap(CharsetCoders.utf8, new DeltaIntListCoder(), tmpdir.childPath("labels"))) {
        assertEquals(data1, dsr.get(key));
      }

    }
  }

}
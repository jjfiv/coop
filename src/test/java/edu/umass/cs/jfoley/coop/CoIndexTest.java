package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.Spawn;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jfoley.
 */
public class CoIndexTest {


  @Test
  public void testCoIndex() throws IOException {
    List<String> docs = new ArrayList<>(Arrays.asList(IO.resource("/gettysburg_address.txt").split("\n+")));

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (CoIndex.Builder builder = new CoIndex.Builder(tmpdir)) {
        for (int i = 0; i < docs.size(); i++) {
          String doc = docs.get(i);
          builder.addDocument(String.format("ga.p%d", i), doc);
        }
      }

      System.err.println(tmpdir.children());

    }

  }


  @Test
  public void testCoIndex2() throws IOException, InterruptedException {
    List<String> docs = new ArrayList<>(Arrays.asList(IO.resource("/gettysburg_address.txt").split("\n+")));

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (CoIndex.VocabBuilder builder = new CoIndex.VocabBuilder(tmpdir)) {
        for (int i = 0; i < docs.size(); i++) {
          String doc = docs.get(i);
          builder.addDocument(String.format("ga.p%d", i), doc);
        }
      }

      System.err.println(tmpdir.children());
      Spawn.doProcess("/bin/ls", tmpdir.getPath(), "-ltr");

      try (CoIndex.VocabReader reader = new CoIndex.VocabReader(tmpdir)) {
        System.err.println(IterableFns.intoList(reader.names.reverseReader.keys()));
        System.err.println(IterableFns.intoList(reader.vocab.reverseReader.keys()));

        for (String term : reader.vocab.reverseReader.keys()) {
          int termId = reader.vocab.reverseReader.get(term);
          PostingMover<PositionsList> pf = reader.positions.get(termId);
          int totalCount = 0;
          for(; !pf.isDone(); pf.next()) {
            int count = pf.getCurrentPosting().size();
            totalCount+=count;
          }

          if(totalCount > 1) {
            System.err.println(term + " " + termId + " " + totalCount);
          }
        }

      }

    }

  }

}
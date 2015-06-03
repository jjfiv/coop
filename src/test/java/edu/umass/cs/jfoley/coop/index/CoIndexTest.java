package edu.umass.cs.jfoley.coop.index;

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
  public void testCoIndex3() throws IOException, InterruptedException {
    List<String> docs = new ArrayList<>(Arrays.asList(IO.resource("/gettysburg_address.txt").split("\n+")));

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IndexBuilder builder = new IndexBuilder(tmpdir)) {
        for (int i = 0; i < docs.size(); i++) {
          String doc = docs.get(i);
          builder.addDocument(String.format("ga.p%d", i), doc);
        }
      }

      System.err.println(tmpdir.children());
      Spawn.doProcess("/bin/ls", tmpdir.getPath(), "-ltr");

      try (IndexReader reader = new IndexReader(tmpdir)) {
        System.err.println(IterableFns.intoList(reader.names.reverseReader.keys()));

        for (String term : reader.positions.keys()) {
          PostingMover<PositionsList> pf = reader.getPositionsMover(term);
          int totalCount = 0;
          for(; !pf.isDone(); pf.next()) {
            int count = pf.getCurrentPosting().size();
            totalCount+=count;
          }

          if(totalCount > 1) {
            System.err.println(term + " " + totalCount);
          }
        }
      }
    }
  }

}
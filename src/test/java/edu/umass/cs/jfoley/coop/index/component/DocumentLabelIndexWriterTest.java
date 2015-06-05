package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class DocumentLabelIndexWriterTest {

  @Test
  public void testSimpleDataInAndOut() throws IOException {
    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (DocumentLabelIndexWriter writer = new DocumentLabelIndexWriter(tmpdir, IndexConfiguration.create())) {
        writer.add("ns", "key", 1);
        writer.add("ns", "key", 2);
        writer.add("ns", "key", 3);
      }
      try (DocumentLabelIndexReader reader = new DocumentLabelIndexReader(tmpdir)) {
        assertEquals(Arrays.asList(1, 2, 3), reader.getMatchingDocs("ns", "key"));
      }
    }
  }
}
package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.postings.extents.Extent;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class StanfordNLPTokenizerTest {
  public static final String SpotDocument = "See spot run. See spot bark.";

  @Test
  public void simpleDocumentTest() {
    StanfordNLPTokenizer tok = new StanfordNLPTokenizer();
    CoopDoc spot = tok.createDocument("spot", SpotDocument);

    assertEquals(
        Arrays.asList("see", "spot", "run", ".", "see", "spot", "bark", "."),
        spot.getTerms());

    // Make sure you count periods if you think my fours are wrong.
    Map<String, List<Extent>> data = Collections.singletonMap(
        "sentence",
        Arrays.asList(new Extent(0, 4), new Extent(4, 8)));

    Map<String, ? extends List<Extent>> tags = spot.getTags();
    assertEquals(data, tags);
  }

  @Test
  public void singleDocumentIndex() throws IOException {
    StanfordNLPTokenizer tok = new StanfordNLPTokenizer();
    CoopDoc spot = tok.createDocument("spot", SpotDocument);

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IndexBuilder builder = new IndexBuilder(tok, tmpdir)) {
        builder.addDocument(spot);
      }
      try (IndexReader reader = new IndexReader(tmpdir)) {
        // Tokenizers should be the same.
        assertEquals(tok.getClass(), reader.getTokenizer().getClass());

        List<String> terms = reader.getCorpus().pullTokens(reader.getDocumentId("spot"));
        assertEquals(spot.getTerms(), terms);
        System.out.println(terms);
      }
    }
  }

}
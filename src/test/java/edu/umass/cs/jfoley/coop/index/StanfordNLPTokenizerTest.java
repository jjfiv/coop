package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.ciir.waltz.postings.extents.SpanList;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
        spot.getTerms().get(tok.getDefaultTermSet()));

    // Make sure you count periods if you think my fours are wrong.
    Map<String, List<Span>> data = Collections.singletonMap(
        "sentence",
        Arrays.asList(new Span(0, 4), new Span(4, 8)));

    System.out.println(spot.getTerms());

    // Interesting, shouldn't bark and run be verbs?
    assertEquals(
        Arrays.asList("VB", "NN", "NN", ".", "VB", "NN", "NN", "."),
        spot.getTerms("pos")
    );

    Map<String, ? extends List<Span>> tags = spot.getTags();
    assertEquals(data, tags);
  }

  @Test
  public void singleDocumentIndex() throws IOException {
    StanfordNLPTokenizer tok = new StanfordNLPTokenizer();
    CoopDoc spot = tok.createDocument("spot", SpotDocument);

    assertNotNull(spot.getTags().get("sentence"));

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IndexBuilder builder = new IndexBuilder(tok, tmpdir)) {
        builder.addDocument(spot);
      }
      try (IndexReader reader = new IndexReader(tmpdir)) {
        // Tokenizers should be the same.
        assertEquals(tok.getClass(), reader.getTokenizer().getClass());

        List<String> terms = reader.getCorpus().pullTokens(reader.getDocumentId("spot"));
        assertEquals(spot.getTerms().get(tok.getDefaultTermSet()), terms);

        // Make sure that our tags got written correctly:
        List<SpanList> data = new ArrayList<>();
        reader.getTag("sentence").collectValues(data::add);
        assertEquals(Collections.singletonList(spot.getTags().get("sentence")), data);
      }
    }
  }

  @Test
  public void interestingLemmaTest() {
    String ApplesSentence = "I have all the apples that you have given anyone.";
    StanfordNLPTokenizer tok = new StanfordNLPTokenizer();
    CoopDoc apples = tok.createDocument("apples", ApplesSentence);
    assertEquals(
        Arrays.asList("i have all the apple that you have give anyone .".split("\\s+")),
        apples.getTerms("lemmas")
    );
  }
}
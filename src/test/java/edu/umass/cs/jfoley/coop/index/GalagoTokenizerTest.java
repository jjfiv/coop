package edu.umass.cs.jfoley.coop.index;

import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
@SuppressWarnings("AssertEqualsBetweenInconvertibleTypes") // Since this is JUnit, when we run it it will just crash.
public class GalagoTokenizerTest {
  public static final String SpotDocument = "<a>See spot run</a>. <b>See spot bark.</b>";

  @Test
  public void testCreateDocument() throws Exception {
    GalagoTokenizer tokenizer = new GalagoTokenizer(Arrays.asList("a", "b"));
    CoopDoc spot = tokenizer.createDocument("spot", SpotDocument);
    // No periods from Galago
    assertEquals(Arrays.asList("see", "spot", "run", "see", "spot", "bark"), spot.getTerms().get(tokenizer.getDefaultTermSet()));
    assertEquals(Collections.singletonList(new Span(0, 3)), spot.getTags().get("a"));
    assertEquals(Collections.singletonList(new Span(3, 6)), spot.getTags().get("b"));
  }
}
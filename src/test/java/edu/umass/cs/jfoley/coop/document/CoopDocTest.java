package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class CoopDocTest {
  public static final String ringo =
      "Richard Starkey, (born 7 July 1940), known professionally as Ringo Starr, is an English drummer, singer, songwriter, and actor who gained worldwide fame as the drummer for the Beatles. On most of the band's albums, he sang lead vocals for one song, including \"With a Little Help from My Friends\", \"Yellow Submarine\" and their cover of \"Act Naturally\". He also wrote the Beatles' songs \"Don\'t Pass Me By\" and \"Octopus\'s Garden\", and is credited as a co-writer of others, such as \"What Goes On\" and \"Flying\". ";

  @Test
  public void testDocument() {
    CoopTokenizer tokenizer = CoopTokenizer.create();

    List<String> expected = new ArrayList<>();
    expected.add("richard starkey , -lrb- bear 7 july 1940 -rrb- , known professionally as ringo starr , be a english drummer , singer , songwriter , and actor who gain worldwide fame as the drummer for the beatles .");
    expected.add("on most of the band 's album , he sing lead vocal for one song , include `` with a little help from my friends '' , `` yellow submarine '' and they cover of `` act naturally '' .");
    expected.add("he also write the beatles ' song `` do not pass i by '' and `` octopus 's garden '' , and be credit as a co-writer of other , such as `` what go on '' and `` flying '' .");

    CoopDoc doc = tokenizer.createDocument("ringo", ringo);
    doc.setIdentifier(132);
    List<List<CoopToken>> sentences = doc.getSentences();
    for (int i = 0; i < sentences.size(); i++) {
      List<CoopToken> coopTokens = sentences.get(i);
      List<String> sl = new ArrayList<>();
      for (CoopToken coopToken : coopTokens) {
        sl.add(coopToken.getTerms().get("lemmas"));
      }

      assertEquals(expected.get(i), StrUtil.join(sl, " "));
    }
  }

}
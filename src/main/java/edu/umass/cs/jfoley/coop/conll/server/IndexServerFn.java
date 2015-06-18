package edu.umass.cs.jfoley.coop.conll.server;

import edu.umass.cs.jfoley.coop.conll.SentenceIndexedToken;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public abstract class IndexServerFn implements ServerFn {
  protected final TermBasedIndexReader index;

  public IndexServerFn(TermBasedIndexReader index) {
    this.index = index;
  }

  public List<List<Parameters>> pullSentenceJSON(List<Integer> ids) throws IOException {
    List<List<Parameters>> sentences = new ArrayList<>();

    for (List<SentenceIndexedToken> sentence : index.pullSentences(ids)) {
      List<Parameters> tokens = new ArrayList<>();
      for (SentenceIndexedToken stoken : sentence) {
        tokens.add(stoken.toJSON());
      }
      sentences.add(tokens);
    }

    return sentences;
  }

}

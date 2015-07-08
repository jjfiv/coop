package edu.umass.cs.jfoley.coop.conll.server;

import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.document.CoopToken;
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

  public synchronized List<List<Parameters>> pullSentenceJSON(List<Integer> ids) throws IOException {
    List<List<Parameters>> sentences = new ArrayList<>();

    for (List<CoopToken> sentence : index.pullSentences(ids)) {
      List<Parameters> tokens = new ArrayList<>();
      for (CoopToken stoken : sentence) {
        tokens.add(stoken.toJSON());
      }
      sentences.add(tokens);
    }

    return sentences;
  }

}

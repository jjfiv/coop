package edu.umass.cs.jfoley.coop.index;

import edu.umass.cs.jfoley.coop.document.CoopDoc;
import org.lemurproject.galago.utility.Parameters;

import java.util.Set;

/**
 * @author jfoley
 */
public interface CoopTokenizer {

  CoopDoc createDocument(String name, String text);
  // tokens, lemmas, pos are all "term" sets, if you will.
  Set<String> getTermSets();
  // which one should we default to with retrieval?
  String getDefaultTermSet();

  static CoopTokenizer create(Parameters argp) {
    if(argp.containsKey("tokenizer")) {
      try {
        return (CoopTokenizer) Class.forName(argp.getString("tokenizer")).newInstance();
      } catch (InstantiationException | ClassNotFoundException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    } else {
      return create();
    }
  }
  static CoopTokenizer create() {
    return new StanfordNLPTokenizer();
  }
}

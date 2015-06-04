package edu.umass.cs.jfoley.coop.index;

import edu.umass.cs.jfoley.coop.document.CoopDoc;
import org.lemurproject.galago.utility.Parameters;

/**
 * @author jfoley
 */
public interface CoopTokenizer {
  CoopDoc createDocument(String name, String text);

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

package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.Tag;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.util.*;

/**
 * @author jfoley
 */
public interface CoopTokenizer {
  List<String> tokenize(String input);
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

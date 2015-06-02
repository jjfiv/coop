package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.jfoley.coop.index.VocabReader;
import edu.umass.cs.jfoley.coop.querying.DocumentAndPosition;
import edu.umass.cs.jfoley.coop.querying.LocatePhrase;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.tokenize.Tokenizer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.io.PrintStream;
import java.util.List;

/**
 * @author jfoley.
 */
public class PhraseFinder extends AppFunction {
  @Override
  public String getName() {
    return "phrase-finder";
  }

  @Override
  public String getHelpString() {
    return makeHelpStr(
        "index", "path to VocabReader index.",
        "query", "a term or phrase query");
  }

  @Override
  public void run(Parameters p, PrintStream output) throws Exception {
    try (VocabReader index = new VocabReader(new Directory(p.getString("index")))) {
      Tokenizer tokenizer = new TagTokenizer();
      List<String> query = tokenizer.tokenize(p.getString("query")).terms;
      for (DocumentAndPosition hit : LocatePhrase.find(index, query)) {
        System.out.println(hit.documentId+" "+hit.matchPosition);
      }
    }
  }

}

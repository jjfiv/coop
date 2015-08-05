package edu.umass.cs.jfoley.coop.front;

import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * @author jfoley
 */
public class IndexFns {
  public static void setup(IndexReader coopIndex, Map<String, ServerFn> methods) {

    methods.put("indexMeta", (p) -> coopIndex.getMetadata());
    methods.put("findKWIC", new FindKWIC(coopIndex));
    methods.put("findPhrase", new FindPhrase(coopIndex));
    // find a document set by AND or OR:
    methods.put("matchDocuments", new MatchDocuments(coopIndex));
    methods.put("rankTermsPMI", new RankTermsPMI(coopIndex));
    methods.put("tokenize", new Tokenize(coopIndex));
  }

  public static class Tokenize extends CoopIndexServerFn {

    protected Tokenize(IndexReader index) {
      super(index);
    }

    @Override
    public Parameters handleRequest(Parameters input) throws IOException, SQLException {
      CoopTokenizer tokenizer = index.getTokenizer();
      CoopDoc doc = tokenizer.createDocument(null, input.getString("text"));
      return doc.toJSON();
    }
  }

}

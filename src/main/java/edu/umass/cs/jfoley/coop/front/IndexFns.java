package edu.umass.cs.jfoley.coop.front;

import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * @author jfoley
 */
public class IndexFns {
  public static void setup(CoopIndex coopIndex, Map<String, ServerFn> methods) {

    methods.put("IndexMeta", (p) -> coopIndex.getMetadata());
    methods.put("FindPhrase", new FindPhrase(coopIndex));

    // find a document set by AND or OR:
    methods.put("MatchDocuments", new MatchDocuments(coopIndex));
    methods.put("PullDocument", new PullDocumentFn(coopIndex));
    methods.put("Tokenize", new Tokenize(coopIndex));
  }

  public static class Tokenize extends CoopIndexServerFn {

    protected Tokenize(CoopIndex index) {
      super(index);
    }

    @Override
    public Parameters handleRequest(Parameters input) throws IOException, SQLException {
      CoopTokenizer tokenizer = index.getTokenizer();
      CoopDoc doc = tokenizer.createDocument(null, input.getString("text"));
      return doc.toJSON();
    }
  }

  private static class PullDocumentFn extends CoopIndexServerFn {
    public PullDocumentFn(CoopIndex coopIndex) {
      super(coopIndex);
    }

    @Override
    public Parameters handleRequest(Parameters input) throws IOException, SQLException {
      CoopDoc doc;
      if(input.containsKey("id")) {
        doc = index.getDocument(input.getInt("id"));
      } else {
        doc = index.getDocument(input.getString("name"));
      }
      if(doc == null) return Parameters.create();
      doc.setRawText(null);
      return doc.toJSON();
    }
  }
}

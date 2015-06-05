package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.lang.Module;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import edu.umass.cs.jfoley.coop.schema.DocVarSchema;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class MTECoopDoc extends Module {
  @Nonnull
  private static CoopDoc createMTE(CoopTokenizer tok, Parameters document, Map<String, DocVarSchema> varSchemas) {
    String text = document.getString("text");
    String name = document.getString("docid");
    CoopDoc doc = tok.createDocument(name, text);

    HashMap<String, DocVar> vars = new HashMap<>();
    for (DocVarSchema docVarSchema : varSchemas.values()) {
      docVarSchema.extract(document, vars);
    }

    doc.setVariables(vars);
    // MTE the raw is the input JSON.
    doc.setRawText(document.toString());

    return doc;
  }

  @Nonnull
  public static CoopDoc createMTE(IndexConfiguration cfg, Parameters document) {
    return createMTE(cfg.tokenizer, document, cfg.documentVariables);
  }
}

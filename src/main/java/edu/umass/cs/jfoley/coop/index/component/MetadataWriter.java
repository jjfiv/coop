package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.Map;

/**
 * @author jfoley
 */
public class MetadataWriter extends IndexItemWriter {
  private final Map<String, DocVarSchema> schema;
  private int collectionLength = 0;
  private int documentCount = 0;
  public Parameters metadata;

  public MetadataWriter(Directory outputDir, CoopTokenizer tokenizer, Map<String, DocVarSchema> schema) {
    super(outputDir, tokenizer);
    this.metadata = Parameters.create();
    this.schema = schema;
  }

  @Override
  public void process(CoopDoc document) throws IOException {
    collectionLength += document.getTerms(tokenizer.getDefaultTermSet()).size();
    documentCount++;
  }

  @Override
  public void close() throws IOException {
    Parameters fieldSchemaJSON = Parameters.create();
    for (String field : schema.keySet()) {
      fieldSchemaJSON.put(field, schema.get(field).toJSON());
    }

    IO.spit(Parameters.parseArray(
        "collectionLength", collectionLength,
        "documentCount", documentCount,
        "schema", fieldSchemaJSON,
        "tokenizer", tokenizer.getClass().getName()
    ).toPrettyString(), outputDir.child("meta.json"));
  }
}

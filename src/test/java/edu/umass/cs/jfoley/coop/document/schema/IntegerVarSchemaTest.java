package edu.umass.cs.jfoley.coop.document.schema;

import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import org.junit.Test;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author jfoley
 */
public class IntegerVarSchemaTest {

  @Test
  public void indexDocumentsAndRetrieveByField() throws IOException {
    CoopTokenizer tok = CoopTokenizer.create();

    List<Parameters> mteStyleDocuments = new ArrayList<>();
    mteStyleDocuments.add(Parameters.parseArray(
        "docid", "one", "text", "one", "ordinal", 1
    ));
    mteStyleDocuments.add(Parameters.parseArray(
        "docid", "two", "text", "two", "ordinal", 2
    ));
    mteStyleDocuments.add(Parameters.parseArray(
        "docid", "three", "text", "three", "ordinal", 3
    ));

    Map<String,DocVarSchema> schemas = new HashMap<>();
    schemas.put("ordinal", IntegerVarSchema.create("ordinal"));

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IndexBuilder builder = new IndexBuilder(tok, tmpdir, schemas)) {
        for (Parameters mteStyleDocument : mteStyleDocuments) {
          builder.addDocument(CoopDoc.createMTE(tok, mteStyleDocument, schemas));
        }
      }

      try (IndexReader reader = new IndexReader(tmpdir)) {
        assertEquals(Collections.singleton("ordinal"), reader.fieldNames());
        assertTrue(reader.fieldSchema("ordinal") instanceof IntegerVarSchema);
        assertEquals(schemas.get("ordinal"), reader.fieldSchema("ordinal"));


      }
    }

  }
}
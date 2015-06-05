package edu.umass.cs.jfoley.coop.document.schema;

import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.jfoley.coop.document.MTECoopDoc;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import edu.umass.cs.jfoley.coop.schema.IntegerVarSchema;
import org.junit.Test;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author jfoley
 */
public class IntegerVarSchemaTest {

  @Test
  public void indexDocumentsAndRetrieveByField() throws IOException {
    IndexConfiguration cfg = IndexConfiguration.create();

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

    cfg.documentVariables.put("ordinal", IntegerVarSchema.create("ordinal"));

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IndexBuilder builder = new IndexBuilder(cfg,tmpdir)) {
        for (Parameters mteStyleDocument : mteStyleDocuments) {
          builder.addDocument(MTECoopDoc.createMTE(cfg, mteStyleDocument));
        }
      }

      try (IndexReader reader = new IndexReader(tmpdir)) {
        assertEquals(Collections.singleton("ordinal"), reader.fieldNames());
        assertTrue(reader.getFieldSchema("ordinal") instanceof IntegerVarSchema);
        assertEquals(cfg.documentVariables.get("ordinal"), reader.getFieldSchema("ordinal"));

        PostingMover<Integer> ordinal = reader.getNumbers("ordinal");
        assertNotNull(ordinal);
        ordinal.execute((document) -> {
          assertTrue(ordinal.matches(document));
          assertEquals(document+1, ordinal.getCurrentPosting().intValue());
        });
      }
    }

  }
}
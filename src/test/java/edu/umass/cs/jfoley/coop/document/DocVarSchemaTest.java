package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.jfoley.coop.document.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import org.junit.Test;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author jfoley
 */
public class DocVarSchemaTest {

  @Test
  public void indexDocumentsAndRetrieveByField() throws IOException {
    CoopTokenizer tok = CoopTokenizer.create();

    List<Parameters> mteStyleDocuments = new ArrayList<>();
    mteStyleDocuments.add(Parameters.parseArray(
        "docid", "blue-wiki",
        "text", "Blue is the colour between violet and green on the optical spectrum of visible light",
        "color", "blue"
    ));
    mteStyleDocuments.add(Parameters.parseArray(
        "docid", "red-wiki",
        "text", "Red is the color at the end of the spectrum of visible light next to orange and opposite violet.",
        "color", "red"
    ));
    mteStyleDocuments.add(Parameters.parseArray(
        "docid", "ocean",
        "text", "The ocean is sometimes green.",
        "color", "blue"
    ));

    Map<String,DocVarSchema> schemas = new HashMap<>();
    schemas.put("color", new CategoricalVarSchema("color", Arrays.asList("red", "blue"), true));

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IndexBuilder builder = new IndexBuilder(tok, tmpdir, schemas)) {
        for (Parameters mteStyleDocument : mteStyleDocuments) {
          builder.addDocument(CoopDoc.createMTE(tok,mteStyleDocument,schemas));
        }
      }

      try (IndexReader reader = new IndexReader(tmpdir)) {
        assertEquals(Collections.singleton("color"), reader.fieldNames());
        assertTrue(reader.fieldSchema("color") instanceof CategoricalVarSchema);
        assertEquals(schemas.get("color"), reader.fieldSchema("color"));

        assertNull(reader.getLabeledDocuments("color", "yellow"));
        assertNull(reader.getLabeledDocuments("flower", "blue"));

        IntList ids = new IntList();
        Mover redDocuments = reader.getLabeledDocuments("color", "red");
        assertNotNull(redDocuments);
        redDocuments.execute(ids::add);
        assertEquals(1, ids.size());
        assertEquals("red-wiki", reader.getDocumentName(ids.get(0)));

      }
    }

  }

}
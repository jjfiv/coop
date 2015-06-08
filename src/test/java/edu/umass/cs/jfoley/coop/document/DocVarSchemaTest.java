package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
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
    IndexConfiguration cfg = IndexConfiguration.create();

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

    cfg.documentVariables.put("color", new CategoricalVarSchema("color", Arrays.asList("red", "blue"), true));

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IndexBuilder builder = new IndexBuilder(cfg, tmpdir)) {
        for (Parameters mteStyleDocument : mteStyleDocuments) {
          builder.addDocument(MTECoopDoc.createMTE(cfg, mteStyleDocument));
        }
      }

      try (IndexReader reader = new IndexReader(tmpdir)) {
        assertEquals(3, reader.getAllDocumentIds().size());
        assertEquals(Collections.singleton("color"), reader.fieldNames());
        assertTrue(reader.getFieldSchema("color") instanceof CategoricalVarSchema);
        assertEquals(cfg.documentVariables.get("color"), reader.getFieldSchema("color"));

        assertNull(reader.getLabeledDocuments("color", "yellow"));
        assertNull(reader.getLabeledDocuments("flower", "blue"));

        IntList redIds = new IntList();
        Mover redDocuments = reader.getLabeledDocuments("color", "red");
        assertNotNull(redDocuments);
        redDocuments.execute(redIds::add);
        assertEquals(1, redIds.size());
        assertEquals("red-wiki", reader.getDocumentName(redIds.get(0)));

        Mover blueDocuments = reader.getLabeledDocuments("color", "blue");
        assertNotNull(blueDocuments);
        IntList blueIds = new IntList();
        blueDocuments.execute(blueIds::add);
        assertEquals(2, blueIds.size());
        assertEquals(new HashSet<>(Arrays.asList("blue-wiki","ocean")), reader.getDocumentNames(blueIds));

      }
    }

  }

}
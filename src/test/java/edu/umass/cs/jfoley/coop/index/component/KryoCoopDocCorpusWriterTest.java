package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.MTECoopDoc;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.schema.DocVarSchema;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import org.junit.Test;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class KryoCoopDocCorpusWriterTest {
  public static final List<Parameters> mteStyleDocuments = new ArrayList<>();
  public static final Map<String, DocVarSchema> schemas = new HashMap<>();
  static {
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

    schemas.put("color", new CategoricalVarSchema("color", Arrays.asList("red", "blue"), true));
  }

  @Test
  public void testCoopDocSerialization() {
    Coder<CoopDoc> coder = new KryoCoder<>(CoopDoc.class);
    IndexConfiguration cfg = IndexConfiguration.create();
    cfg.documentVariables.putAll(schemas);

    for (Parameters mteStyleDocument : mteStyleDocuments) {
      CoopDoc current = MTECoopDoc.createMTE(cfg, mteStyleDocument);
      CoopDoc translated = coder.read(coder.write(current));
      assertEquals(current.toJSON().toPrettyString(), translated.toJSON().toPrettyString());
      assertEquals(current, translated);
    }

  }

  @Test
  public void testCoopDocFetching() throws IOException {
    IndexConfiguration cfg = IndexConfiguration.create();
    cfg.documentVariables.putAll(schemas);

    List<CoopDoc> docs = new ArrayList<>();
    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IndexBuilder builder = new IndexBuilder(cfg, tmpdir)) {
        for (Parameters mteStyleDocument : mteStyleDocuments) {
          CoopDoc current = MTECoopDoc.createMTE(cfg, mteStyleDocument);
          docs.add(current);
          builder.addDocument(current);
        }
      }

      try (IndexReader reader = new IndexReader(tmpdir)) {
        for (int i = 0; i < docs.size(); i++) {
          assertEquals(docs.get(i), reader.getDocument(i));
        }
      }
    }
  }
}
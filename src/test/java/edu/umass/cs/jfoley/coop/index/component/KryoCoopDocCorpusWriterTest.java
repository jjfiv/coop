package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import edu.umass.cs.jfoley.coop.document.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import edu.umass.cs.jfoley.coop.index.IndexReader;
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

    CoopTokenizer tok = CoopTokenizer.create();
    for (Parameters mteStyleDocument : mteStyleDocuments) {
      CoopDoc current = CoopDoc.createMTE(tok, mteStyleDocument, schemas);
      CoopDoc translated = coder.read(coder.write(current));
      assertEquals(current.toJSON().toPrettyString(), translated.toJSON().toPrettyString());
      assertEquals(current, translated);
    }

  }

  @Test
  public void testCoopDocFetching() throws IOException {
    CoopTokenizer tok = CoopTokenizer.create();

    List<CoopDoc> docs = new ArrayList<>();
    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IndexBuilder builder = new IndexBuilder(tok, tmpdir, schemas)) {
        for (Parameters mteStyleDocument : mteStyleDocuments) {
          CoopDoc current = CoopDoc.createMTE(tok, mteStyleDocument, schemas);
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
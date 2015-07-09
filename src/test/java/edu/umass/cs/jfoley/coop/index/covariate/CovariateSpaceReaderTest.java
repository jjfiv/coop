package edu.umass.cs.jfoley.coop.index.covariate;

import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.postings.docset.DocumentSetReader;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVar;
import edu.umass.cs.jfoley.coop.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import edu.umass.cs.jfoley.coop.schema.IntegerVarSchema;
import org.junit.Test;
import org.lemurproject.galago.utility.Parameters;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * @author jfoley
 */
public class CovariateSpaceReaderTest {

  @Test
  public void testGet() throws Exception {

    Map<Integer,CoopDoc> docs = new HashMap<>();
    IntegerVarSchema xSchema = IntegerVarSchema.create("x");
    CategoricalVarSchema ySchema = CategoricalVarSchema.create("y", Parameters.create());
    try(TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try(MapCovariateSpaceWriter<Integer,String> writer = new MapCovariateSpaceWriter<>(
          tmpdir, IndexConfiguration.create(),
          IntegerVarSchema.create("x"),
          CategoricalVarSchema.create("y", Parameters.create())
      )) {

        for (int i = 0; i < 100; i++) {
          CoopDoc doc = new CoopDoc();
          doc.setIdentifier(i);

          Map<String, DocVar> vars = new HashMap<>();
          vars.put("x", xSchema.createValue(i % 10));
          vars.put("y", ySchema.createValue(String.format("%02d", i % 20)));
          doc.setVariables(vars);

          writer.process(doc);
          docs.put(i, doc);
        }
      }
      // finish writing docs
      try (CovariateSpaceReader<Integer,String> reader = new CovariateSpaceReader<>(
          xSchema, ySchema,
              new DocumentSetReader<>(
              new CovariableCoder<Integer, String>(xSchema.getCoder().lengthSafe(), ySchema.getCoder().lengthSafe()),
              tmpdir, "covar.x.y"))) {

        for (CoopDoc coopDoc : docs.values()) {
          Mover mov = reader.get(coopDoc.getIdentifier() % 10, String.format("%02d", coopDoc.getIdentifier() % 20));
          mov.moveToAbsolute(coopDoc.getIdentifier());
          assertTrue(mov.matches(coopDoc.getIdentifier()));
        }

      }


    }
  }
}
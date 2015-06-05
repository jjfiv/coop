package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.general.IndexItemWriter;
import edu.umass.cs.jfoley.coop.index.general.MapCovariateSpaceWriter;
import edu.umass.cs.jfoley.coop.schema.CovariateSpaceConfiguration;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class CovariateSpaceWriters extends IndexItemWriter {
  public final List<MapCovariateSpaceWriter> writers;

  @SuppressWarnings("unchecked")
  public CovariateSpaceWriters(Directory outputDir, IndexConfiguration cfg) throws IOException {
    super(outputDir, cfg);

    writers = new ArrayList<>();
    for (CovariateSpaceConfiguration covariateSpace : cfg.covariateSpaces) {
      writers.add(
          new MapCovariateSpaceWriter(outputDir, cfg, covariateSpace.xSchema, covariateSpace.ySchema)
      );
    }
  }

  @Override
  public void process(CoopDoc document) throws IOException {
    for (MapCovariateSpaceWriter writer : writers) {
      writer.process(document);
    }
  }

  @Override
  public void close() throws IOException {
    for (MapCovariateSpaceWriter writer : writers) {
      writer.close();
    }
  }
}

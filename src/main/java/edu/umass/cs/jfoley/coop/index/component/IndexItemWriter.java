package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author jfoley
 */
public abstract class IndexItemWriter implements Closeable {
  protected final Directory outputDir;
  protected final IndexConfiguration cfg;

  public IndexItemWriter(Directory outputDir, IndexConfiguration cfg) {
    this.outputDir = outputDir;
    this.cfg = cfg;
  }

  public abstract void process(CoopDoc document) throws IOException;
  @Override
  public abstract void close() throws IOException;
}

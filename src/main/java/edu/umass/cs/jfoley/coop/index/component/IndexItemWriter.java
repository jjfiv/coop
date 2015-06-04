package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author jfoley
 */
public abstract class IndexItemWriter implements Closeable {
  protected final Directory outputDir;
  protected final CoopTokenizer tokenizer;

  public IndexItemWriter(Directory outputDir, CoopTokenizer tokenizer) {
    this.outputDir = outputDir;
    this.tokenizer = tokenizer;
  }

  public abstract void process(CoopDoc document);
  @Override
  public abstract void close() throws IOException;
}

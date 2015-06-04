package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.archive.ZipWriter;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;

import java.io.IOException;

/**
 * @author jfoley
 */
public class RawCorpusWriter extends IndexItemWriter {
  private final ZipWriter rawCorpusWriter;

  public RawCorpusWriter(Directory outputDir, CoopTokenizer tokenizer) throws IOException {
    super(outputDir, tokenizer);
    this.rawCorpusWriter = new ZipWriter(outputDir.childPath("raw.zip"));
  }

  @Override
  public void process(CoopDoc doc) throws IOException {
    rawCorpusWriter.writeUTF8(doc.getName(), doc.getRawText());
  }

  @Override
  public void close() throws IOException {
    rawCorpusWriter.close();
  }
}

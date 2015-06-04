package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;
import edu.umass.cs.jfoley.coop.index.IdMaps;

import java.io.IOException;

/**
 * @author jfoley
 */
public class NamesWriter extends IndexItemWriter {
  private final IdMaps.Writer<String> names;
  public NamesWriter(Directory outputDir, CoopTokenizer tokenizer) throws IOException {
    super(outputDir, tokenizer);
    this.names = IdMaps.openWriter(
        outputDir.childPath("names"),
        FixedSize.ints,
        CharsetCoders.utf8Raw);
  }

  @Override
  public void process(CoopDoc document) throws IOException {
    names.put(document.getIdentifier(), document.getName());
  }

  @Override
  public void close() throws IOException {
    names.close();
  }
}

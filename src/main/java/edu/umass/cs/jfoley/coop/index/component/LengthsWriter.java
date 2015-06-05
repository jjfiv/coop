package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.StreamingPostingBuilder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.general.IndexItemWriter;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class LengthsWriter extends IndexItemWriter {
  private final StreamingPostingBuilder<String, Integer> writer;

  public LengthsWriter(Directory outputDir, IndexConfiguration cfg) throws IOException {
    super(outputDir, cfg);
    this.writer = new StreamingPostingBuilder<>(
        CharsetCoders.utf8Raw,
        VarUInt.instance,
        GalagoIO.getRawIOMapWriter(outputDir.childPath("lengths"))
    );
  }

  @Override
  public void process(CoopDoc document) {
    for (Map.Entry<String, List<String>> kv : document.getTerms().entrySet()) {
      writer.add(kv.getKey(), document.getIdentifier(), kv.getValue().size());
    }
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}

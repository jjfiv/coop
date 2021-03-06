package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.SpanListCoder;
import edu.umass.cs.ciir.waltz.io.postings.streaming.StreamingPostingBuilder;
import edu.umass.cs.ciir.waltz.postings.extents.SpanList;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.general.IndexItemWriter;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;

import java.io.IOException;
import java.util.Map;

/**
 * @author jfoley
 */
public class TagIndexWriter extends IndexItemWriter {
  private final StreamingPostingBuilder<String, SpanList> writer;

  public TagIndexWriter(Directory outputDir, IndexConfiguration cfg) throws IOException {
    super(outputDir, cfg);
    this.writer = new StreamingPostingBuilder<>(
        CharsetCoders.utf8,
        new SpanListCoder(),
        GalagoIO.getRawIOMapWriter(outputDir.childPath("tags"))
    );
  }

  @Override
  public void process(CoopDoc document) {
    for (Map.Entry<String, SpanList> kv : document.getTags().entrySet()) {
      writer.add(kv.getKey(), document.getIdentifier(), kv.getValue());
    }
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}

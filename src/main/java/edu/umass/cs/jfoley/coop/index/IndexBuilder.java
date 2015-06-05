package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.lang.Builder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.component.*;
import edu.umass.cs.jfoley.coop.index.corpus.ZipTokensCorpusWriter;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley.
 */
public class IndexBuilder implements Closeable, Builder<IndexReader> {
  private final Directory outputDir;
  private final IndexConfiguration cfg;
  private int documentId = 0;
  private final List<IndexItemWriter> writers;

  public IndexBuilder(IndexConfiguration cfg, Directory outputDir) throws IOException {
    this.outputDir = outputDir;
    this.cfg = cfg;

    this.writers = new ArrayList<>();
    // terms & tags:
    writers.add(new PositionsSetWriter(outputDir, cfg));
    writers.add(new TagIndexWriter(outputDir, cfg));

    // document vars:
    writers.add(new DocumentLabelIndexWriter(outputDir, cfg));
    writers.add(new NumericalVarWriter(outputDir, cfg));

    // lengths, names:
    writers.add(new LengthsWriter(outputDir, cfg));
    writers.add(new NamesWriter(outputDir, cfg));

    // corpus:
    writers.add(new RawCorpusWriter(outputDir, cfg));
    writers.add(new KryoCoopDocCorpusWriter(outputDir, cfg));
    writers.add(new ZipTokensCorpusWriter(outputDir, cfg));

    // co-variate spaces
    writers.add(new CovariateSpaceWriters(outputDir, cfg));

    // TODO: have every writer have a unique name and a piece of JSON to contribute.
    MetadataWriter metadataWriter = new MetadataWriter(outputDir, cfg);
    writers.add(metadataWriter);
  }

  public void addDocument(String name, String text) throws IOException {
    CoopDoc document = cfg.tokenizer.createDocument(name, text);
    document.setRawText(text);
    addDocument(document);
  }

  public void addDocument(CoopDoc doc) throws IOException {
    int currentId = documentId++;
    doc.setIdentifier(currentId);

    for (IndexItemWriter writer : writers) {
      writer.process(doc);
    }
  }

  @Override
  public void close() throws IOException {
    for (IndexItemWriter builder : writers) {
      builder.close();
    }
  }

  @Override
  public IndexReader getOutput() {
    try {
      return new IndexReader(outputDir);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}

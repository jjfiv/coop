package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.lang.Builder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import edu.umass.cs.jfoley.coop.index.component.*;
import edu.umass.cs.jfoley.coop.index.corpus.ZipTokensCorpusWriter;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley.
 */
public class IndexBuilder implements Closeable, Builder<IndexReader> {
  private final Directory outputDir;
  private final CoopTokenizer tokenizer;
  private int documentId = 0;
  private final List<IndexItemWriter> writers;

  public IndexBuilder(CoopTokenizer tok, Directory outputDir) throws IOException {
    this(tok, outputDir, Collections.emptyMap());
  }
  public IndexBuilder(CoopTokenizer tok, Directory outputDir, Map<String, DocVarSchema> fieldSchema) throws IOException {
    this.outputDir = outputDir;
    this.tokenizer = tok;

    this.writers = new ArrayList<>();
    writers.add(new PositionsSetWriter(outputDir, tokenizer));
    writers.add(new DocumentLabelIndexWriter(outputDir, tokenizer));
    writers.add(new TagIndexWriter(outputDir, tokenizer));
    writers.add(new LengthsWriter(outputDir, tokenizer));
    writers.add(new RawCorpusWriter(outputDir, tokenizer));
    writers.add(new ZipTokensCorpusWriter(outputDir, tokenizer));
    writers.add(new NamesWriter(outputDir, tokenizer));

    // TODO: have every writer have a unique name and a piece of JSON to contribute.
    MetadataWriter metadataWriter = new MetadataWriter(outputDir, tokenizer, fieldSchema);
    writers.add(metadataWriter);
  }

  public void addDocument(String name, String text) throws IOException {
    CoopDoc document = tokenizer.createDocument(name, text);
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

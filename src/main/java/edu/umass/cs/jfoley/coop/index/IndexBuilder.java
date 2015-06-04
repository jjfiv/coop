package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.archive.ZipWriter;
import ciir.jfoley.chai.lang.Builder;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import edu.umass.cs.jfoley.coop.index.component.*;
import org.lemurproject.galago.utility.Parameters;

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
  private final ZipWriter rawCorpusWriter;
  private final CoopTokenizer tokenizer;
  private final ZipWriter tokensCorpusWriter;
  private final ListCoder<String> tokensCodec;
  private final IdMaps.Writer<String> names;
  private int documentId = 0;
  private int collectionLength = 0;
  private final Map<String, DocVarSchema> fieldSchema;
  private final List<IndexItemWriter> writers;

  public IndexBuilder(CoopTokenizer tok, Directory outputDir) throws IOException {
    this(tok, outputDir, Collections.emptyMap());
  }
  public IndexBuilder(CoopTokenizer tok, Directory outputDir, Map<String, DocVarSchema> fieldSchema) throws IOException {
    this.outputDir = outputDir;
    this.fieldSchema = fieldSchema;

    this.rawCorpusWriter = new ZipWriter(outputDir.childPath("raw.zip"));
    this.tokensCorpusWriter = new ZipWriter(outputDir.childPath("tokens.zip"));
    this.tokenizer = tok;
    this.names = IdMaps.openWriter(outputDir.childPath("names"), FixedSize.ints, CharsetCoders.utf8Raw);
    this.tokensCodec = new ListCoder<>(CharsetCoders.utf8LengthPrefixed);
    this.writers = new ArrayList<>();

    writers.add(new PositionsSetWriter(outputDir, tokenizer));
    writers.add(new DocumentLabelIndex.Writer(outputDir, tokenizer));
    writers.add(new TagIndexWriter(outputDir, tokenizer));
    writers.add(new LengthsWriter(outputDir, tokenizer));

  }

  public void addDocument(String name, String text) throws IOException {
    CoopDoc document = tokenizer.createDocument(name, text);
    document.setRawText(text);
    addDocument(document);
  }

  public void addDocument(CoopDoc doc) throws IOException {
    Map<String, List<String>> terms = doc.getTerms();
    int currentId = documentId++;
    doc.setIdentifier(currentId);

    // corpus
    rawCorpusWriter.writeUTF8(doc.getName(), doc.getRawText());
    // write length to flat lengths file.
    names.put(currentId, doc.getName());

    collectionLength += terms.size();

    for (IndexItemWriter writer : writers) {
      writer.process(doc);
    }

    // So we don't have to pay tokenization time in the second pass.
    tokensCorpusWriter.write(Integer.toString(currentId), outputStream -> {
      tokensCodec.write(outputStream, terms.get(tokenizer.getDefaultTermSet()));
    });
  }

  @Override
  public void close() throws IOException {
    System.err.println("Begin closing writers!");
    rawCorpusWriter.close();
    names.close();
    tokensCorpusWriter.close();
    for (IndexItemWriter builder : writers) {
      builder.close();
    }

    Parameters fieldSchemaJSON = Parameters.create();
    for (String field : fieldSchema.keySet()) {
      fieldSchemaJSON.put(field, fieldSchema.get(field).toJSON());
    }

    IO.spit(Parameters.parseArray(
        "collectionLength", collectionLength,
        "documentCount", documentId,
        "schema", fieldSchemaJSON,
        "tokenizer", tokenizer.getClass().getName()
    ).toPrettyString(), outputDir.child("meta.json"));
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

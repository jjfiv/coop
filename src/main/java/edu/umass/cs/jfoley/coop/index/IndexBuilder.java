package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.archive.ZipWriter;
import ciir.jfoley.chai.lang.Builder;
import edu.umass.cs.ciir.waltz.coders.kinds.*;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.SpanListCoder;
import edu.umass.cs.ciir.waltz.io.postings.StreamingPostingBuilder;
import edu.umass.cs.ciir.waltz.postings.extents.SpanList;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.postings.positions.SimplePositionsList;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVar;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import edu.umass.cs.jfoley.coop.document.schema.CategoricalVarSchema;
import org.lemurproject.galago.utility.Parameters;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley.
 */
public class IndexBuilder implements Closeable, Flushable, Builder<IndexReader> {
  private final Directory outputDir;
  private final ZipWriter rawCorpusWriter;
  private final CoopTokenizer tokenizer;
  private final ZipWriter tokensCorpusWriter;
  private final StreamingPostingBuilder<String, Integer> lengthWriter;
  private final ListCoder<String> tokensCodec;
  private final IdMaps.Writer<String> names;
  private final Map<String, StreamingPostingBuilder<String,PositionsList>> positionsBuilders;
  private final StreamingPostingBuilder<String, SpanList> tagsBuilder;
  private int documentId = 0;
  private int collectionLength = 0;
  private final Map<String, DocVarSchema> fieldSchema;
  private final DocumentLabelIndex.Writer doclabelWriter;

  public IndexBuilder(CoopTokenizer tok, Directory outputDir) throws IOException {
    this(tok, outputDir, Collections.emptyMap());
  }
  public IndexBuilder(CoopTokenizer tok, Directory outputDir, Map<String, DocVarSchema> fieldSchema) throws IOException {
    this.outputDir = outputDir;
    this.fieldSchema = fieldSchema;

    doclabelWriter = new DocumentLabelIndex.Writer(
        GalagoIO.getIOMapWriter(
            DocumentLabelIndex.NamespacedLabel.coder,
            new DeltaIntListCoder(),
            outputDir.childPath("doclabels")
        )
    );
    this.rawCorpusWriter = new ZipWriter(outputDir.childPath("raw.zip"));
    this.tokensCorpusWriter = new ZipWriter(outputDir.childPath("tokens.zip"));
    this.tokenizer = tok;
    this.lengthWriter = new StreamingPostingBuilder<>(
        CharsetCoders.utf8Raw,
        VarUInt.instance,
        GalagoIO.getRawIOMapWriter(outputDir.childPath("lengths"))
    );
    this.names = IdMaps.openWriter(outputDir.childPath("names"), FixedSize.ints, CharsetCoders.utf8Raw);
    this.tokensCodec = new ListCoder<>(CharsetCoders.utf8LengthPrefixed);
    this.positionsBuilders = new HashMap<>();
    for (String tokenSet : this.tokenizer.getTermSets()) {
      positionsBuilders.put(
          tokenSet,
          new StreamingPostingBuilder<>(
              CharsetCoders.utf8Raw,
              new PositionsListCoder(),
              GalagoIO.getRawIOMapWriter(outputDir.childPath(tokenSet+".positions")))
      );
    }
    tagsBuilder = new StreamingPostingBuilder<>(
        CharsetCoders.utf8Raw,
        new SpanListCoder(),
        GalagoIO.getRawIOMapWriter(outputDir.childPath("tags"))
    );
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
    lengthWriter.add("doc", currentId, terms.size());
    names.put(currentId, doc.getName());

    collectionLength += terms.size();

    // collect tags:
    for (Map.Entry<String, SpanList> kv : doc.getTags().entrySet()) {
      tagsBuilder.add(kv.getKey(), currentId, kv.getValue());
    }

    // collect variables:
    for (DocVar docVar : doc.getVariables()) {
      if(docVar.getSchema() instanceof CategoricalVarSchema) {
        String field = docVar.getName();
        String label = (String) docVar.get();
        doclabelWriter.add(field, label, currentId);
      }
    }

    for (Map.Entry<String, List<String>> kv : doc.getTerms().entrySet()) {
      addToPositionsBuilder(currentId, kv.getKey(), kv.getValue());
    }

    // So we don't have to pay tokenization time in the second pass.
    tokensCorpusWriter.write(Integer.toString(currentId), outputStream -> {
      tokensCodec.write(outputStream, terms.get(tokenizer.getDefaultTermSet()));
    });
  }

  public void addToPositionsBuilder(int currentId, String tokenSet, List<String> terms) {
    StreamingPostingBuilder<String, PositionsList> positionsBuilder = this.positionsBuilders.get(tokenSet);

    // collection position vectors:
    Map<String, IntList> data = new HashMap<>();
    for (int i = 0, termsSize = terms.size(); i < termsSize; i++) {
      String term = terms.get(i);
      MapFns.extendCollectionInMap(data, term, i, (GenerateFn<IntList>) IntList::new);
    }
    // Add position vectors to builder:
    for (Map.Entry<String, IntList> kv : data.entrySet()) {
      positionsBuilder.add(
          kv.getKey(),
          currentId,
          new SimplePositionsList(kv.getValue()));
    }
  }

  @Override
  public void close() throws IOException {
    System.err.println("Begin closing writers!");
    rawCorpusWriter.close();
    lengthWriter.close();
    doclabelWriter.close();
    tagsBuilder.close();
    names.close();
    tokensCorpusWriter.close();
    for (Closeable builder : positionsBuilders.values()) {
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
  public void flush() throws IOException {

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

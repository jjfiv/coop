package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.archive.ZipWriter;
import ciir.jfoley.chai.lang.Builder;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.StreamingPostingBuilder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.postings.positions.SimplePositionsList;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley.
 */
public class IndexBuilder implements Closeable, Flushable, Builder<IndexReader> {
  private final Directory outputDir;
  private final ZipWriter rawCorpusWriter;
  private final TagTokenizer tokenizer;
  private final ZipWriter tokensCorpusWriter;
  private final StreamingPostingBuilder<String, Integer> lengthWriter;
  private final ListCoder<String> tokensCodec;
  private final IdMaps.Writer<String> names;
  private final StreamingPostingBuilder<String, PositionsList> positionsBuilder;
  private int documentId = 0;
  private int collectionLength = 0;

  public IndexBuilder(Directory outputDir) throws IOException {
    this.outputDir = outputDir;
    this.rawCorpusWriter = new ZipWriter(outputDir.childPath("raw.zip"));
    this.tokensCorpusWriter = new ZipWriter(outputDir.childPath("tokens.zip"));
    this.tokenizer = new TagTokenizer();
    this.lengthWriter = new StreamingPostingBuilder<>(
        CharsetCoders.utf8Raw,
        VarUInt.instance,
        GalagoIO.getRawIOMapWriter(outputDir.childPath("lengths"))
    );
    this.names = IdMaps.openWriter(outputDir.childPath("names"), FixedSize.ints, CharsetCoders.utf8Raw);
    this.tokensCodec = new ListCoder<>(CharsetCoders.utf8LengthPrefixed);
    this.positionsBuilder = new StreamingPostingBuilder<>(
        CharsetCoders.utf8Raw,
        new PositionsListCoder(),
        //GenKeyDiskMap.Writer.createNew(outputDir.childPath("positions", ))
        GalagoIO.getRawIOMapWriter(outputDir.childPath("positions")));
  }

  public void addDocument(String name, String text) throws IOException {
    List<String> terms = tokenizer.tokenize(text).terms;
    int currentId = documentId++;

    // corpus
    rawCorpusWriter.writeUTF8(name, text);
    // write length to flat lengths file.
    lengthWriter.add("doc", currentId, terms.size());
    names.put(currentId, name);

    collectionLength += terms.size();

    // collection position vectors:
    Map<String, IntList> data = new HashMap<>();
    for (int i = 0, termsSize = terms.size(); i < termsSize; i++) {
      String term = terms.get(i);
      MapFns.extendCollectionInMap(data, term, i, (GenerateFn<IntList>) IntList::new);
    }
    // Add position vectors to builder:
    for (Map.Entry<String, IntList> kv : data.entrySet()) {
      this.positionsBuilder.add(
          kv.getKey(),
          currentId,
          new SimplePositionsList(kv.getValue()));
    }

    // So we don't have to pay tokenization time in the second pass.
    tokensCorpusWriter.write(Integer.toString(currentId), outputStream -> {
      tokensCodec.write(outputStream, terms);
    });
  }

  @Override
  public void close() throws IOException {
    System.err.println("Begin closing writers!");
    rawCorpusWriter.close();
    lengthWriter.close();
    names.close();
    tokensCorpusWriter.close();
    positionsBuilder.close();

    IO.spit(Parameters.parseArray(
        "collectionLength", collectionLength,
        "documentCount", documentId
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

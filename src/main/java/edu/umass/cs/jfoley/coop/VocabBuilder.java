package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.archive.ZipArchive;
import ciir.jfoley.chai.io.archive.ZipArchiveEntry;
import ciir.jfoley.chai.io.archive.ZipWriter;
import ciir.jfoley.chai.lang.Builder;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.GenKeyDiskMap;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.StreamingPostingBuilder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.postings.positions.SimplePositionsList;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author jfoley.
 */
public class VocabBuilder implements Closeable, Flushable, Builder<VocabReader> {
  private final Directory outputDir;
  private final ZipWriter rawCorpusWriter;
  private final TagTokenizer tokenizer;
  private final ZipWriter tokensCorpusWriter;
  private final StreamingPostingBuilder<String, Integer> lengthWriter;
  private final ListCoder<String> tokensCodec;
  private final TObjectIntHashMap<String> tokenCounts;
  private final IdMaps.Writer<String> names;
  private final IdMaps.Writer<String> vocab;
  private final GenKeyDiskMap.Writer<List<Integer>> termIdCorpus;
  private final StreamingPostingBuilder<Integer, PositionsList> positionsBuilder;
  private int documentId = 0;
  private int collectionLength = 0;

  public VocabBuilder(Directory outputDir) throws IOException {
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
    this.vocab = IdMaps.openWriter(outputDir.childPath("vocab"), FixedSize.ints, CharsetCoders.utf8Raw);
    this.tokensCodec = new ListCoder<>(CharsetCoders.utf8LengthPrefixed);
    this.tokenCounts = new TObjectIntHashMap<>();
    this.termIdCorpus = GenKeyDiskMap.Writer.createNew(outputDir.childPath("termIdCorpus"), new ListCoder<>(VarUInt.instance));
    this.positionsBuilder = new StreamingPostingBuilder<>(
        FixedSize.ints,
        new PositionsListCoder(),
        //GenKeyDiskMap.Writer.createNew(outputDir.childPath("positions", ))
        GalagoIO.getRawIOMapWriter(outputDir.childPath("positions")));
  }

  public void addDocument(String name, String text) throws IOException {
    System.out.println("+doc:\t" + name + "\t" + StrUtil.preview(text, 60));
    List<String> terms = tokenizer.tokenize(text).terms;
    int currentId = documentId++;

    // corpus
    rawCorpusWriter.writeUTF8(name, text);
    // write length to flat lengths file.
    lengthWriter.add("doc", currentId, terms.size());
    names.put(currentId, name);

    collectionLength += terms.size();
    for (String term : terms) {
      tokenCounts.adjustOrPutValue(term, 1, 1);
    }
    // So we don't have to pay tokenization time in the second pass.
    tokensCorpusWriter.write(Integer.toString(currentId), outputStream -> {
      tokensCodec.write(outputStream, terms);
    });
  }

  public List<Pair<String, Integer>> extractVocabularyAndSort() {
    List<Pair<String, Integer>> localVocab = new ArrayList<>();
    // extract tokenCounts
    tokenCounts.forEachEntry((term, freq) -> {
      localVocab.add(Pair.of(term, freq));
      return true;
    });
    // sort by frequency highest to lowest
    Collections.sort(
        localVocab,
        (lhs, rhs) -> Integer.compare(rhs.getValue(), lhs.getValue()));

    // free what memory we can
    tokenCounts.clear();
    return localVocab;
  }

  @Override
  public void close() throws IOException {
    rawCorpusWriter.close();
    lengthWriter.close();
    names.close();
    tokensCorpusWriter.close();

    List<Pair<String, Integer>> localVocab = extractVocabularyAndSort();
    TObjectIntHashMap<String> termRanks = new TObjectIntHashMap<>();
    // save vocabulary ids:
    for (int rank = 0; rank < localVocab.size(); rank++) {
      Pair<String, Integer> kv = localVocab.get(rank);
      vocab.put(rank, kv.left);
      termRanks.put(kv.left, rank);
    }
    vocab.close();

    try (ZipArchive tokensCorpus = ZipArchive.open(outputDir.child("tokens.zip"))) {
      for (ZipArchiveEntry entry : tokensCorpus.listFileEntries()) {
        int docId = Integer.parseInt(entry.getName());
        IntList tokenVector = new IntList();
        try (InputStream stream = entry.getInputStream()) {
          for (String term : tokensCodec.read(stream)) {
            tokenVector.add(termRanks.get(term));
          }
        }

        // with each document:
        // collection position vectors:
        Map<Integer, IntList> data = new HashMap<>();
        for (int i = 0; i < tokenVector.size(); i++) {
          MapFns.extendCollectionInMap(data, tokenVector.get(i), i, new IntList());
        }
        // Add position vectors to builder:
        for (Map.Entry<Integer, IntList> kv : data.entrySet()) {
          this.positionsBuilder.add(
              kv.getKey(),
              docId,
              new SimplePositionsList(kv.getValue()));
        }

        termIdCorpus.put((long) docId, tokenVector);
      }
    }
    termIdCorpus.close();
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
  public VocabReader getOutput() {
    try {
      return new VocabReader(outputDir);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.archive.ZipArchive;
import ciir.jfoley.chai.io.archive.ZipArchiveEntry;
import ciir.jfoley.chai.io.archive.ZipWriter;
import ciir.jfoley.chai.lang.Builder;
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
    System.err.println("Begin closing writers!");
    rawCorpusWriter.close();
    lengthWriter.close();
    names.close();
    tokensCorpusWriter.close();

    List<Pair<String, Integer>> localVocab = extractVocabularyAndSort();
    HashMap<String,Integer> termRanks = new HashMap<>();
    //TObjectIntHashMap<String> termRanks = new TObjectIntHashMap<>();
    // save vocabulary ids:
    for (int rank = 0; rank < localVocab.size(); rank++) {
      Pair<String, Integer> kv = localVocab.get(rank);
      vocab.put(rank, kv.left);
      termRanks.put(kv.left, rank);
    }
    vocab.close();

    System.err.println(termRanks.size());

    System.err.println("Begin positions pass!");
    try (ZipArchive tokensCorpus = ZipArchive.open(outputDir.child("tokens.zip"))) {
      for (ZipArchiveEntry entry : tokensCorpus.listFileEntries()) {
        int docId = Integer.parseInt(entry.getName());
        System.err.println("readDocument:"+docId);
        List<String> termVector;
        try (InputStream stream = entry.getInputStream()) {
          termVector = tokensCodec.read(stream);
        }

        System.err.println("translateDocument:"+docId);
        System.err.println("  collect position vectors:"+docId);
        // collection position vectors and translate the document:
        Map<Integer, IntList> data = new HashMap<>();
        IntList tokenVector = new IntList();
        tokenVector.resize(termVector.size());
        for (int i = 0, termVectorSize = termVector.size(); i < termVectorSize; i++) {
          if (i % 10000 == 0) {
            System.err.println(i);
          }
          String term = termVector.get(i);
          tokenVector.add(termRanks.get(term));
          MapFns.extendCollectionInMap(data, termRanks.get(term), i, (GenerateFn<IntList>) IntList::new);
        }
        System.err.println("  add position vectors to builder:"+docId);
        // Add position vectors to builder:
        for (Map.Entry<Integer, IntList> kv : data.entrySet()) {
          this.positionsBuilder.add(
              kv.getKey(),
              docId,
              new SimplePositionsList(kv.getValue()));
        }

        System.err.println("  add to termIdCorpus:"+docId);
        termIdCorpus.put((long) docId, tokenVector);
      }
    }
    System.err.println("termIdCorpus.close()!");
    termIdCorpus.close();
    System.err.println("positionsBuilder.close()!");
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

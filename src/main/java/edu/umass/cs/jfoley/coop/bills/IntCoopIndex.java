package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsIndexFile;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.front.CoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsReader;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import edu.umass.cs.jfoley.coop.tokenization.GalagoTokenizer;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class IntCoopIndex implements CoopIndex {
  public static final String positionsFileName = "positions.waltz";
  public final Directory baseDir;
  final IntVocabBuilder.IntVocabReader corpus;
  final IdMaps.Reader<String> names;
  final IdMaps.Reader<String> vocab;
  IOMap<Integer, PostingMover<PositionsList>> positions;

  PhraseHitsReader entities;
  TermPositionsIndex positionsIndex;
  PhrasePositionsIndex entitiesIndex;

  private void tryBuildNames() throws IOException {
    if(!baseDir.child("names.fwd").exists()) {
      try (IdMaps.Writer<String> namesWriter = GalagoIO.openIdMapsWriter(baseDir.childPath("names"), FixedSize.ints, CharsetCoders.utf8)) {
        corpus.forEachName((kv) -> {
          try {
            namesWriter.put(kv.getKey(), kv.getValue());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      }
    }
  }

  private void tryBuildVocab() throws IOException {
    if(!baseDir.child("vocab.fwd").exists()) {
      try (IdMaps.Writer<String> termsWriter = GalagoIO.openIdMapsWriter(baseDir.childPath("vocab"), FixedSize.ints, CharsetCoders.utf8)) {
        try (PrintWriter out = IO.openPrintWriter(baseDir.childPath("vocab.tsv.gz"))){
          corpus.forEachTerm((kv) -> {
            try {
              out.println(kv.getKey() + "\t" + kv.getValue());
              termsWriter.put(kv.getKey(), kv.getValue());
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
        }
      }
    }
  }

  public IntCoopIndex(Directory baseDir) throws IOException {
    this.baseDir = baseDir;

    this.corpus = new IntVocabBuilder.IntVocabReader(baseDir);
    tryBuildNames();
    tryBuildVocab();
    this.names = GalagoIO.openIdMapsReader(baseDir.childPath("names"), FixedSize.ints, CharsetCoders.utf8);
    this.vocab = GalagoIO.openIdMapsReader(baseDir.childPath("vocab"), FixedSize.ints, CharsetCoders.utf8);

    if(baseDir.child(positionsFileName+".keys").exists()) {
      this.positions = PositionsIndexFile.openReader(FixedSize.ints, baseDir, positionsFileName);
      this.positionsIndex = new TermPositionsIndex(vocab, positions, getTokenizer(), getCorpus());
    }

    if(baseDir.child("dbpedia.positions.keys").exists()) {
      entities = new PhraseHitsReader(this, baseDir, "dbpedia");
      entitiesIndex = new PhrasePositionsIndex(entities, vocab, entities.getPhraseVocab(), entities.getDocumentsByPhrase());
    }
  }

  @Override
  public CoopTokenizer getTokenizer() {
    return new GalagoTokenizer();
  }

  @Override
  public CoopDoc getDocument(int id) {
    CoopDoc doc = new CoopDoc();
    doc.setIdentifier(id);
    try {
      doc.setTerms("tokens", translateToTerms(new IntList(corpus.getDocument(id))));
    } catch (IOException e) {
      e.printStackTrace(System.err);
      return null;
    }
    return doc;
  }

  @Override
  public IntList translateFromTerms(List<String> query) throws IOException {
    TObjectIntHashMap<String> translator = termIdTranslator(query);
    IntList output = new IntList(query.size());
    for (String q : query) {
      int id = translator.get(q);
      if(id == translator.getNoEntryValue()) {
        output.add(-1);
      } else {
        output.add(translator.get(q));
      }
    }
    return output;
  }

  @Override
  public List<String> translateToTerms(IntList termIds) throws IOException {
    TIntObjectHashMap<String> translator = termTranslator(termIds);
    ArrayList<String> terms = new ArrayList<>(termIds.size());
    for (int termId : termIds) {
      terms.add(translator.get(termId));
    }
    return terms;
  }

  @Override
  public CoopDoc getDocument(String name) {
    try {
      Integer id = names.getReverse(name);
      if(id == null) return null;
      return getDocument(id);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }


  @Override
  public TermPositionsIndex getPositionsIndex(String termKind) {
    if(!termKind.equals("lemmas")) return null;
    return positionsIndex;
  }

  @Override
  public PhrasePositionsIndex getEntitiesIndex() {
    return entitiesIndex;
  }

  @Override
  public Iterable<Pair<Integer, String>> lookupNames(IntList hits) throws IOException {
    return names.getForward(hits);
  }

  @Override
  public Iterable<Pair<TermSlice, IntList>> pullTermSlices(Iterable<TermSlice> slices) {
    return IterableFns.map(slices, (slice) -> {
      try {
        int width = slice.size();
        IntList words = corpus.getSlice(slice.document, slice.start, width);
        return Pair.of(slice, words);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public Iterable<Pair<String, Integer>> lookupTermIds(List<String> query) throws IOException {
    return vocab.getReverse(query);
  }

  @Override
  public long getCollectionLength() throws IOException {
    return corpus.numberOfTermOccurrences();
  }

  public TIntObjectHashMap<String> termTranslator(IntList termIds) throws IOException {
    TIntObjectHashMap<String> data = new TIntObjectHashMap<>();
    for (Pair<Integer, String> kv : lookupTerms(termIds)) {
      data.put(kv.getKey(), kv.getValue());
    }
    return data;
  }
  public TObjectIntHashMap<String> termIdTranslator(List<String> termIds) throws IOException {
    TObjectIntHashMap<String> data = new TObjectIntHashMap<>();
    for (Pair<String, Integer> kv : vocab.getReverse(termIds)) {
      data.put(kv.getKey(), kv.getValue());
    }
    return data;
  }

  public int getTermId(String term) throws IOException {
    Integer id = vocab.getReverse(term);
    if(id == null) return -1;
    return id;
  }

  @Override
  public Iterable<Pair<Integer, String>> lookupTerms(IntList termIds) throws IOException {
    return this.vocab.getForward(termIds);
  }


  @Override
  public Parameters getMetadata() {
    return Parameters.create();
  }

  @Override
  public void close() throws IOException {
    this.vocab.close();
    this.names.close();
    IO.close(this.positions);
    this.corpus.close();
    IO.close(this.entities);
  }

  public IntVocabBuilder.IntVocabReader getCorpus() {
    return corpus;
  }

  public PhraseHitsReader getEntities() {
    return entities;
  }

  public IdMaps.Reader<String> getTermVocabulary() {
    return vocab;
  }

  public static String parseDBPediaTitle(String input) {
    String base = StrUtil.takeBefore(input.replace('_', ' '), '(');
    // make "el ni&ntilde;o" -> "el nino"
    return StrUtil.collapseSpecialMarks(base.trim());
  }

  public PhraseDetector loadPhraseDetector(int N, IntCoopIndex target) throws IOException {
    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();
    System.err.println("Total: " + names.size());
    int count = IntMath.fromLong(names.size());
    Debouncer msg = new Debouncer(500);
    PhraseDetector detector = new PhraseDetector(N);

    long start = System.currentTimeMillis();
    TObjectIntHashMap<String> vocabLookup = new TObjectIntHashMap<>(IntMath.fromLong(target.vocab.size()));
    for (Pair<Integer, String> kv : target.vocab.items()) {
      vocabLookup.put(StrUtil.collapseSpecialMarks(kv.getValue()), kv.getKey());
    }
    long end = System.currentTimeMillis();
    System.err.println("# preload vocab: "+(end-start)+"ms.");
    int docNameIndex = 0;
    int ND = corpus.numberOfDocuments();
    for (Pair<Integer,String> pair : names.items()) {
      int phraseId = pair.left;
      String name = pair.right;


      docNameIndex++;
      String text = parseDBPediaTitle(name);
      List<String> query = tokenizer.tokenize(text).terms;
      int size = query.size();
      if(size == 0 || size > N) continue;
      IntList qIds = new IntList(query.size());
      for (String str : query) {
        int tid = vocabLookup.get(str);
        if(tid == vocabLookup.getNoEntryValue()) {
          qIds = null;
          break;
        }
        qIds.push(tid);
      }
      // vocab mismatch; phrase-match therefore not possible
      if(qIds == null) continue;

      detector.addPattern(qIds, phraseId);

      assert(pair.left < ND);

      if(msg.ready()) {
        System.err.println(text);
        //System.err.println(getDocument(phraseId));
        System.err.println(query);
        System.err.println(qIds);
        System.err.println(msg.estimate(docNameIndex, count));
        System.err.println(detector);
      }
    }
    return detector;
  }

  public IdMaps.Reader<String> getNames() {
    return names;
  }
}

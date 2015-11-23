package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
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
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsIndexFile;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.experiments.IndexedSDMQuery;
import edu.umass.cs.jfoley.coop.front.CoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.front.QueryEngine;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsReader;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import edu.umass.cs.jfoley.coop.tokenization.GalagoTokenizer;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author jfoley
 */
public class IntCoopIndex implements CoopIndex {
  public static final String positionsFileName = "positions.waltz";
  public final Directory baseDir;
  final IntVocabBuilder.IntVocabReader corpus;
  final IdMaps.Reader<String> names;
  final IdMaps.IdReader<String> vocab;
  IOMap<Integer, PostingMover<Integer>> counts;
  IOMap<Integer, PostingMover<PositionsList>> positions;

  PhraseHitsReader entities;
  TermPositionsIndex positionsIndex;
  PhrasePositionsIndex entitiesIndex;

  public QueryEngine.QCNode<Double> smooth(String method, QueryEngine.QCNode<Integer> child) {
    switch (method) {
      case "linear":
      case "jm":
        return new QueryEngine.LinearSmoothingNode(child);
      case "dirichlet":
        return new QueryEngine.DirichletSmoothingNode(child);
      default:
        throw new RuntimeException("Smoothing: "+method+" does not exist!");
    }
  }

  public List<ScoredDocument> searchQL(String query, String smoothingMethod, int numResults) throws IOException {
    TermPositionsIndex index = getPositionsIndex("lemmas");

    if(query.isEmpty()) return Collections.emptyList();

    TagTokenizer tokenizer = new TagTokenizer();
    List<String> terms = tokenizer.tokenize(query).terms;
    if(terms.isEmpty()) return Collections.emptyList();

    List<QueryEngine.QCNode<Double>> pnodes = new ArrayList<>();
    IntList termIds = index.translateFromTerms(terms);
    for (int termId : termIds) {
      if(termId < 0) {
        continue;
      }
      QueryEngine.QCNode<Integer> countNode = index.getUnigram(termId);
      if(countNode == null) {
        countNode = QueryEngine.MissingTermNode.instance;
      }
      pnodes.add(smooth(smoothingMethod, countNode));
    }
    if(pnodes.isEmpty()) { return Collections.emptyList(); }
    QueryEngine.QCNode<Double> ql = new QueryEngine.CombineNode(pnodes);
    Mover mover = QueryEngine.createMover(ql);

    TopKHeap<ScoredDocument> sdocs = new TopKHeap<>(numResults, new ScoredDocument.ScoredDocumentComparator());
    ql.setup(index);
    for(mover.start(); !mover.isDone(); mover.next()) {
      int id = mover.currentKey();
      double score = Objects.requireNonNull(ql.calculate(index, id));
      sdocs.add(new ScoredDocument(id, score));
    }

    List<ScoredDocument> sorted = sdocs.getSorted();
    for (int i = 0; i < sorted.size(); i++) {
      ScoredDocument scoredDocument = sorted.get(i);
      int doc = (int) scoredDocument.document;
      scoredDocument.documentName = getNames().getForward(doc);
      scoredDocument.rank = i+1;
    }

    return sorted;
  }
  public static Parameters searchQL(IntCoopIndex target, Parameters p) throws IOException {
    TermPositionsIndex index = target.getPositionsIndex("lemmas");

    String q = p.getString("query");
    int num = p.get("n", 200);
    if(q.isEmpty()) return Parameters.create();

    List<Parameters> jsonPages = new ArrayList<>();

    TagTokenizer tokenizer = new TagTokenizer();
    List<String> terms = tokenizer.tokenize(q).terms;
    if(terms.isEmpty()) return Parameters.create();

    List<QueryEngine.QCNode<Double>> pnodes = new ArrayList<>();
    IntList termIds = index.translateFromTerms(terms);
    for (int termId : termIds) {
      if(termId == -1) continue;
      pnodes.add(new QueryEngine.LinearSmoothingNode(index.getUnigram(termId)));
    }
    if(pnodes.isEmpty()) {
      return Parameters.create();
    }
    QueryEngine.QCNode<Double> ql = new QueryEngine.CombineNode(pnodes);
    Mover mover = QueryEngine.createMover(ql);

    TopKHeap<ScoredDocument> sdocs = new TopKHeap<>(num, new ScoredDocument.ScoredDocumentComparator());
    ql.setup(index);
    for(mover.start(); !mover.isDone(); mover.next()) {
      int id = mover.currentKey();
      double score = Objects.requireNonNull(ql.calculate(index, id));
      sdocs.add(new ScoredDocument(id, score));
    }
    boolean pullTerms = p.get("pullTerms", true);

    List<ScoredDocument> sorted = sdocs.getSorted();
    for (int i = 0; i < sorted.size(); i++) {
      ScoredDocument scoredDocument = sorted.get(i);
      if(scoredDocument == null) continue;
      Parameters jdoc = Parameters.create();
      int doc = (int) scoredDocument.document;
      jdoc.put("score", scoredDocument.score);
      jdoc.put("rank", i+1);
      jdoc.put("name", target.getNames().getForward(doc));
      if(pullTerms) {
        jdoc.put("terms", target.translateToTerms(new IntList(target.getCorpus().getDocument(doc))));
      }
      jsonPages.add(jdoc);
    }

    return Parameters.parseArray(
        "queryTerms", terms,
        "queryIds", termIds,
        "docs", jsonPages
    );
  }

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
    this.vocab = GalagoIO.openIdMapsReader(baseDir.childPath("vocab"), FixedSize.ints, CharsetCoders.utf8).getCached(200_000);

    if(baseDir.child("counts.keys").exists()) {
      this.counts = IndexedSDMQuery.SDMPartReaders.countIndexCfg.openReader(baseDir, "counts");
    }

    if(baseDir.child(positionsFileName+".keys").exists()) {
      this.positions = PositionsIndexFile.openReader(FixedSize.ints, baseDir, positionsFileName);
      this.positionsIndex = new TermPositionsIndex(vocab, counts, positions, getTokenizer(), getCorpus());
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
    return vocab.translateReverse(query, -1);
  }

  @Override
  public List<String> translateToTerms(IntList termIds) throws IOException {
    return vocab.translateForward(termIds, null);
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
    try {
      return Parameters.parseArray(
          "uniqueWords", vocab.size(),
          "documentCount", names.size(),
          "tokenizer", getTokenizer().getClass().toString(),
          "collectionLength", getCollectionLength()
          );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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

  public IdMaps.IdReader<String> getTermVocabulary() {
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

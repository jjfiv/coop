package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
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
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsReader;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import edu.umass.cs.jfoley.coop.tokenization.StanfordNLPTokenizer;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class IntCoopIndex implements CoopIndex {
  public static final String positionsFileName = "positions.waltz";
  final Directory baseDir;
  final IntVocabBuilder.IntVocabReader corpus;
  final IdMaps.Reader<String> names;
  final IdMaps.Reader<String> vocab;
  IOMap<Integer, PostingMover<PositionsList>> positions;

  PhraseHitsReader entities;
  TermPositionsIndex positionsIndex;

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
      this.positionsIndex = new TermPositionsIndex(vocab, positions);
    }

    if(baseDir.child("entities.positions.keys").exists()) {
      entities = new PhraseHitsReader(this, baseDir, "entities");
    }
  }

  @Override
  public CoopTokenizer getTokenizer() {
    return new StanfordNLPTokenizer();
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
    for (Pair<String, Integer> kv : lookupTermIds(termIds)) {
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
}

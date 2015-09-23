package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.KeyMetadata;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;
import edu.umass.cs.jfoley.coop.bills.IntVocabBuilder;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * Part of an index that represents a mapping between a single term and positions:
 * @author jfoley
 */
public class TermPositionsIndex {
  final IdMaps.Reader<String> vocab;
  final IOMap<Integer, PostingMover<PositionsList>> positions;
  private final CoopTokenizer tokenizer;
  private final IntVocabBuilder.IntVocabReader corpus;
  LoadingCache<Integer, KeyMetadata<?>> pmeta;

  public TermPositionsIndex(IdMaps.Reader<String> vocab, IOMap<Integer, PostingMover<PositionsList>> positions, CoopTokenizer tokenizer, IntVocabBuilder.IntVocabReader corpus) throws IOException {
    this.vocab = vocab;
    this.positions = positions;
    this.tokenizer = tokenizer;
    this.corpus = corpus;
    long cacheSize = 1_000_000;
    pmeta = Caffeine.newBuilder().maximumSize(cacheSize).build((id) -> {
      try {
        PostingMover<PositionsList> mover = positions.get(id);
        if (mover == null) return null;
        return mover.getMetadata();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    ForkJoinPool.commonPool().execute(() -> {
      long start = System.currentTimeMillis();
      try {
        for (Pair<Integer, PostingMover<PositionsList>> item : positions.items()) {
          // Put as many items in the cache as will fit:
          if(pmeta.estimatedSize() == cacheSize) return;
          pmeta.put(item.getKey(), Objects.requireNonNull(Objects.requireNonNull(item.getValue()).getMetadata()));
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      long end = System.currentTimeMillis();
      System.out.println("Metadata bg-thread cache-warmup: " + (end - start) + "ms.");
    });
  }

  public PostingMover<PositionsList> getPositionsMover(String queryTerm) throws IOException {
    Integer id = vocab.getReverse(queryTerm);
    if(id == null) return null;
    return getPositionsMover(id);
  }

  public PostingMover<PositionsList> getPositionsMover(int termId) throws IOException {
    if (termId < 0) return null;
    return positions.get(termId);
  }

  public TIntIntHashMap getCollectionFrequencies(IntList ids) throws IOException {
    TIntIntHashMap output = new TIntIntHashMap(ids.size());
    for (int id : ids) {
      KeyMetadata<?> meta = pmeta.get(id);
      assert (meta != null);
      assert (meta instanceof PositionsCountMetadata);
      PositionsCountMetadata pmc = (PositionsCountMetadata) meta;
      output.put(id, pmc.totalCount);
    }
    return output;
  }

  public int collectionFrequency(int termId) {
    KeyMetadata<?> meta = pmeta.get(termId);
    assert (meta != null);
    assert (meta instanceof PositionsCountMetadata);
    PositionsCountMetadata pmc = (PositionsCountMetadata) meta;
    return pmc.totalCount;
  }

  public ArrayList<DocumentResult<Integer>> locatePhrase(IntList queryIds) throws IOException {
    ArrayList<DocumentResult<Integer>> output = new ArrayList<>();

    QueryEngine.QueryRepr repr = new QueryEngine.QueryRepr();
    QueryEngine.PhraseNode phrase = new QueryEngine.PhraseNode(queryIds, repr);

    System.out.println("query: "+queryIds);
    System.out.println("unique: "+repr.getUniqueTerms());
    System.out.println("mapping: "+phrase.termIdMapping);

    if(queryIds.size() == 0) {
      return output;
    }
    if(queryIds.size() == 1) {
      PostingMover<PositionsList> only = IterableFns.first(repr.getMovers(this));
      for(only.start(); !only.isDone(); only.next()) {
        int doc = only.currentKey();
        PositionsList positions = only.getPosting(doc);
        for (int i = 0; i < positions.size(); i++) {
          output.add(new DocumentResult<>(doc, positions.getPosition(i)));
        }
      }
      return output;
    }

    ArrayList<PostingMover<PositionsList>> iters = repr.getMovers(this);
    AllOfMover<?> andMover = new AllOfMover<>(iters);

    for(andMover.start(); !andMover.isDone(); andMover.next()) {
      int doc = andMover.currentKey();
      phrase.process(doc, iters, output::add);
    }
    return output;
  }

  public CoopTokenizer getTokenizer() {
    return tokenizer;
  }

  public TObjectIntHashMap<String> termIdTranslator(List<String> termIds) throws IOException {
    TObjectIntHashMap<String> data = new TObjectIntHashMap<>();
    for (Pair<String, Integer> kv : vocab.getReverse(termIds)) {
      data.put(kv.getKey(), kv.getValue());
    }
    return data;
  }

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

  public int translateFromTerm(String term) throws IOException {
    Integer x = vocab.getReverse(term);
    if(x == null) return -1;
    return x;
  }

  public String translateToTerm(int termId) throws IOException {
    return vocab.getForward(termId);
  }

  public TIntObjectHashMap<String> termTranslator(IntList termIds) throws IOException {
    TIntObjectHashMap<String> data = new TIntObjectHashMap<>();
    for (Pair<Integer, String> kv : vocab.getForward(termIds)) {
      data.put(kv.getKey(), kv.getValue());
    }
    return data;
  }

  public List<String> translateToTerms(IntList termIds) throws IOException {
    TIntObjectHashMap<String> translator = termTranslator(termIds);
    ArrayList<String> terms = new ArrayList<>(termIds.size());
    for (int termId : termIds) {
      terms.add(translator.get(termId));
    }
    return terms;
  }

  public int getLength(int doc) throws IOException {
    return corpus.getLength(doc);
  }
}

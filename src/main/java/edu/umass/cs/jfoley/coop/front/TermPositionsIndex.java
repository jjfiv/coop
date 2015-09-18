package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.sys.KeyMetadata;
import edu.umass.cs.ciir.waltz.sys.positions.PositionsCountMetadata;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Part of an index that represents a mapping between a single term and positions:
 * @author jfoley
 */
public class TermPositionsIndex {
  final IdMaps.Reader<String> vocab;
  final IOMap<Integer, PostingMover<PositionsList>> positions;
  private final CoopTokenizer tokenizer;
  HashMap<Integer, KeyMetadata<?>> pmeta;

  public TermPositionsIndex(IdMaps.Reader<String> vocab, IOMap<Integer, PostingMover<PositionsList>> positions, CoopTokenizer tokenizer) throws IOException {
    this.vocab = vocab;
    this.positions = positions;
    this.tokenizer = tokenizer;
    long start = System.currentTimeMillis();
    pmeta = new HashMap<>();
    for (Pair<Integer, PostingMover<PositionsList>> kv : this.positions.items()) {
      pmeta.put(kv.getKey(), kv.getValue().getMetadata());
    }
    long end = System.currentTimeMillis();
    System.out.println("Caching metadata: " + (end - start) + "ms.");
  }

  public PostingMover<PositionsList> getPositionsMover(String queryTerm) throws IOException {
    return getPositionsMover(vocab.getReverse(queryTerm));
  }

  PostingMover<PositionsList> getPositionsMover(int termId) throws IOException {
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
    return vocab.getReverse(term);
  }

  public String translateToTerm(int termId) throws IOException {
    return vocab.getForward(termId);
  }
}

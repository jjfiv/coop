package edu.umass.cs.jfoley.coop.bdaat;

import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.util.IterableFns;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongDoubleHashMap;
import org.lemurproject.galago.core.index.Index;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.iterator.LengthsIterator;
import org.lemurproject.galago.core.retrieval.iterator.ScoreIterator;
import org.lemurproject.galago.core.retrieval.processing.ProcessingModel;
import org.lemurproject.galago.core.retrieval.processing.ScoringContext;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.retrieval.query.NodeParameters;
import org.lemurproject.galago.utility.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley.
 */
public class BDAATProcessingModel extends ProcessingModel {
  LocalRetrieval retrieval;
  Index index;

  public BDAATProcessingModel(LocalRetrieval lr) {
    retrieval = lr;
    this.index = retrieval.getIndex();
  }

  @Override
  public ScoredDocument[] execute(Node queryTree, Parameters queryParams) throws Exception {
    assert(queryTree.getOperator().equals("combine"));
    List<Node> children = queryTree.getInternalNodes();

    // This model uses the simplest ScoringContext
    ScoringContext context = new ScoringContext();

    // Number of documents requested.
    int requested = queryParams.get("requested", 1000);
    int batchSize = queryParams.get("batchSize", 20);
    int docsAtATime = queryParams.get("docsAtATime", 100);
    boolean annotate = queryParams.get("annotate", false);

    // Maintain a queue of candidates
    TopKHeap<ScoredDocument> heap = new TopKHeap<>(requested, new ScoredDocument.ScoredDocumentComparator());
    //FixedSizeMinHeap<ScoredDocument> queue = new FixedSizeMinHeap<>(ScoredDocument.class, requested, new ScoredDocument.ScoredDocumentComparator());

    List<ScoreIterator> iterators = new ArrayList<>();
    double normed = 1.0 / (double) children.size();
    for (List<Node> nodes : IterableFns.batches(children, batchSize)) {
      Node comb = new Node("combine", nodes);
      NodeParameters np = comb.getNodeParameters();
      np.set("norm", false);
      for (int i = 0; i < nodes.size(); i++) {
        np.set(Integer.toString(i), normed);
      }
      iterators.add((ScoreIterator) retrieval.createIterator(queryParams, comb));
    }

    LengthsIterator iter = index.getLengthsIterator();

    TLongDoubleHashMap accumulators = new TLongDoubleHashMap();
    long start = 0;
    while(true) {
      // shared document that every subquery reached:
      long firstIncompleteDocument = Long.MAX_VALUE;
      for (ScoreIterator iterator : iterators) {
        for (int i = 0; i < docsAtATime; i++) {
          long document = i + start;
          context.document = document;
          iter.syncTo(document);
          if(iter.isDone()) break;
          iterator.syncTo(document);
          //if(iterator.hasMatch(context)) // don't need to check because OR
          double score = iterator.score(context);
          accumulators.adjustOrPutValue(document, score, score);
        }
        iterator.movePast(start+docsAtATime);
        firstIncompleteDocument = Math.min(firstIncompleteDocument, iterator.currentCandidate());
      }
      start += docsAtATime;

      TLongArrayList completedKeys = new TLongArrayList();
      List<ScoredDocument> toOffer = new ArrayList<>();
      final long docFilter = firstIncompleteDocument;
      accumulators.forEachKey((key) -> {
        if(key < docFilter) { completedKeys.add(key); }
        return true;
      });
      for (int i = 0; i < completedKeys.size(); i++) {
        long docNo = completedKeys.getQuick(i);
        double score = accumulators.get(docNo);
        accumulators.remove(docNo);
        toOffer.add(new ScoredDocument(docNo, score));
      }
      heap.addAll(toOffer);

      if(firstIncompleteDocument == Long.MAX_VALUE) break;
    }

    // finish up.
    accumulators.forEachEntry((key, score) -> {
      heap.offer(new ScoredDocument(key, score));
      return true;
    });
    accumulators.clear();

    if (heap.size() == 0) {
      return null;
    }

    List<ScoredDocument> sorted = heap.getSorted();
    ScoredDocument[] items = new ScoredDocument[sorted.size()];

    for (int i = 0; i < items.length; i++) {
      ScoredDocument doc = sorted.get(i);
      doc.rank = i+1;
      items[i] = doc;
    }

    return items;
  }
}

package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.phrase.OrderedWindow;
import edu.umass.cs.ciir.waltz.postings.extents.SpanIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley
 */
public class FindPhrase extends CoopIndexServerFn {
  protected FindPhrase(CoopIndex index) {
    super(index);
  }

  public List<DocumentResult<Integer>> locatePhrase(IntList queryIds) throws IOException {
    ArrayList<DocumentResult<Integer>> output = new ArrayList<>();
    IntList uniqueTerms = new IntList();
    IntList termIdMapping = new IntList();
    for (int i = 0; i < queryIds.size(); i++) {
      int term_i = queryIds.getQuick(i);
      int first_pos = queryIds.indexOf(term_i);
      termIdMapping.add(first_pos);
      if(first_pos == i) {
        uniqueTerms.add(term_i);
      }
    }

    System.out.println("query: "+queryIds);
    System.out.println("unique: "+uniqueTerms);
    System.out.println("mapping: "+termIdMapping);

    ArrayList<PostingMover<PositionsList>> iters = new ArrayList<>();
    for (int uniqueTerm : uniqueTerms) {
      PostingMover<PositionsList> iter = index.getPositionsMover("lemmas", uniqueTerm);
      int numDocuments = iter != null ? iter.totalKeys() : 0;
      System.out.println("termId: " + uniqueTerm + " numDocs: " + numDocuments);
      if (iter == null) {
        return Collections.emptyList();
      }
      iters.add(iter);
    }

    AllOfMover<?> andMover = new AllOfMover<>(iters);

    for(andMover.start(); !andMover.isDone(); andMover.next()) {
      int doc = andMover.currentKey();
      ArrayList<SpanIterator> posIters = new ArrayList<>(termIdMapping.size());
      for (int i = 0; i < termIdMapping.size(); i++) {
        posIters.add(iters.get(termIdMapping.getQuick(i)).getCurrentPosting().getSpanIterator());
      }
      for (int position : OrderedWindow.findIter(posIters, 1)) {
        System.err.println("D"+doc+" "+position);
        output.add(new DocumentResult<>(doc, position));
      }
    }
    return output;
  }

  @Override
  public Parameters handleRequest(Parameters p) throws IOException, SQLException {
    final Parameters output = Parameters.create();
    final int count = p.get("count", 200);
    assert(count > 0);
    final String termKind = p.get("termKind", "lemmas");
    final boolean pullSlices = p.get("pullSlices", false);
    final boolean scoreTerms = p.get("scoreTerms", false);
    final int numTerms = p.get("numTerms", 30);
    final int minTermFrequency = p.get("minTermFrequency", 4);

    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = ListFns.map(tokenizer.createDocument("tmp", p.getString("query")).getTerms(termKind), String::toLowerCase);
    output.put("queryTerms", query);

    IntList queryIds = index.translateFromTerms(query);


    long startTime = System.currentTimeMillis();
    List<DocumentResult<Integer>> hits = locatePhrase(queryIds);
    long endTime = System.currentTimeMillis();
    int queryFrequency = hits.size();
    output.put("queryFrequency", queryFrequency);
    output.put("queryTime", (endTime-startTime));
    System.out.println("phraseTime: "+(endTime-startTime));

    final TIntObjectHashMap<Parameters> hitInfos = new TIntObjectHashMap<>();
    // only return 200 results
    for (DocumentResult<Integer> hit : ListFns.slice(hits, 0, 200)) {
      Parameters doc = Parameters.create();
      doc.put("id", hit.document);
      doc.put("loc", hit.value);
      hitInfos.put(hit.document, doc);
    }

    for (Pair<Integer, String> kv : index.lookupNames(new IntList(hitInfos.keys()))) {
      hitInfos.get(kv.left).put("name", kv.right);
    }

    TIntIntHashMap termProxCounts = new TIntIntHashMap();

    // also pull terms if we want:
    if(scoreTerms || pullSlices) {
      int leftWidth = Math.max(0, p.get("leftWidth", 1));
      int rightWidth = Math.max(0, p.get("rightWidth", 1));
      int phraseWidth = queryIds.size();

      /*List<TermSlice> slices = new ArrayList<>(count);
      for (DocumentResult<Integer> result : ListFns.slice(hits.right, 0, count)) {
        int pos = result.value;
        slices.add(new TermSlice(result.document,
            pos - leftWidth, pos + rightWidth + 1));
      }*/

      // Lazy convert hits to slices:
      Iterable<TermSlice> slices = IterableFns.map(hits, (result) -> {
        int pos = result.value;
        TermSlice slice = new TermSlice(result.document,
            pos - leftWidth, pos + rightWidth + phraseWidth);
        System.err.println("#D"+result.document+" "+pos);
        assert(slice.size() == leftWidth+rightWidth+phraseWidth);
        return slice;
      });

      // Lazy pull and calculate most frequent terms:
      for (Pair<TermSlice, IntList> pair : index.pullTermSlices(slices)) {
        System.err.println(queryIds);
        System.err.println(pair.right);
        for (Integer queryId : queryIds) {
          assert(pair.right.contains(queryId));
        }
        int firstHit = pair.right.indexOf(queryIds.get(0));
        System.out.println(pair.left);
        System.out.println("first-hit: "+firstHit);
        if(pullSlices) {
          Parameters docp = hitInfos.get(pair.left.document);
          if(docp != null) {
            docp.put("terms", index.translateToTerms(pair.right));
          }
        }
        if(scoreTerms) {
          for (int term : pair.right) {
            assert(term >= 0);
            if(queryIds.contains(term)) continue;
            termProxCounts.adjustOrPutValue(term, 1, 1);
          }
        }
      }
    }

    double collectionLength = index.getCollectionLength();
    TopKHeap<PMITerm<Integer>> topTerms = new TopKHeap<>(numTerms);
    if(scoreTerms) {
      termProxCounts.forEachEntry((term, frequency) -> {
        if(frequency > minTermFrequency) {
          topTerms.add(new PMITerm<>(term, index.collectionFrequency(term), queryFrequency, frequency, collectionLength));
        }
        return true;
      });

      IntList termIds = new IntList(numTerms);
      for (PMITerm<Integer> topTerm : topTerms) {
        termIds.add(topTerm.term);
      }
      TIntObjectHashMap<String> terms = new TIntObjectHashMap<>();
      for (Pair<Integer, String> kv : index.lookupTerms(termIds)) {
        terms.put(kv.getKey(), kv.getValue());
      }

      List<Parameters> termResults = new ArrayList<>();
      for (PMITerm<Integer> pmiTerm : topTerms.getUnsortedList()) {
        Parameters tjson = pmiTerm.toJSON();
        tjson.put("term", terms.get(pmiTerm.term));
        //tjson.put("docs", new ArrayList<>(termInfos.get(pmiTerm.term)));
        termResults.add(tjson);
      }
      output.put("termResults", termResults);
    }

    output.put("results", ListFns.slice(new ArrayList<>(hitInfos.valueCollection()), 0, 200));
    return output;
  }
}

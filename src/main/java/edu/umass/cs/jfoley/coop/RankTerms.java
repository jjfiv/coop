package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.querying.LocatePhrase;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.tokenize.Tokenizer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class RankTerms extends AppFunction {
  @Override
  public String getName() {
    return "rank-terms";
  }

  @Override
  public String getHelpString() {
    return makeHelpStr(
        "index", "path to VocabReader index.",
        "leftWidth", "width of left candidates, [default=5]",
        "rightWidth", "width of right candidates, [default=5]",
        "hitLimit", "number of candidate query-hits to process, [default=None]",
        "limit", "number of terms to print out, [default=20]",
        // todo stop results
        // todo sample values instead of doing them all
        "query", "a term or phrase query; we'll tokenize for you; e.g. --query=\"hello world\" or --query=hello"
    );
  }

  @Override
  public void run(Parameters p, PrintStream output) throws Exception {
    IndexReader index = new IndexReader(new Directory(p.getString("index")));
    int leftWidth = p.get("leftWidth", 5);
    assert(leftWidth >= 0);
    int rightWidth = p.get("rightWidth", 5);
    assert(rightWidth >= 0);
    int limit = p.get("limit", 20);
    assert(limit > 0);
    int hitLimit = p.get("hitLimit", Integer.MAX_VALUE);
    assert(hitLimit > 0);

    Tokenizer tokenizer = new TagTokenizer();
    List<String> query = tokenizer.tokenize(p.getString("query")).terms;
    System.err.println("I parsed your query as the following terms: "+ StrUtil.join(query, " "));


    Pair<Long, List<DocumentResult<Integer>>> hits = Timing.milliseconds(() -> LocatePhrase.find(index, query));
    int queryFrequency = hits.right.size();
    System.err.println("Run query in "+hits.left+" ms. "+hits.right.size()+" hits found!");

    // build slices from the results, based on arguments to this file:
    List<TermSlice> slices = new ArrayList<>();
    for (DocumentResult<Integer> hit : ListFns.take(hits.right, hitLimit)) {
      slices.add(new TermSlice(
          hit.document,
          hit.value - leftWidth,
          hit.value + query.size() + rightWidth));
    }

    // Now score the nearby terms!
    final TObjectIntHashMap<String> termProxCounts = new TObjectIntHashMap<>();
    long candidateFindingTime = Timing.milliseconds(() -> {
      index.getCorpus().forTermInSlice(slices, (term) ->  {
        termProxCounts.adjustOrPutValue(term, 1, 1);
      });
      /*for (TermSlice slice : slices) {
        for (String term : index.pullTokens(slice)) {
          termProxCounts.adjustOrPutValue(term, 1, 1);
        }
      }*/
    });
    System.err.println("Found "+termProxCounts.size() + " candidates in "+candidateFindingTime+" ms.");

    // Okay, now actually score these candidates!
    double collectionLength = index.getCollectionLength();
    TopKHeap<PMITerm> topTerms = new TopKHeap<>(limit, Comparing.defaultComparator());

    long scoringTime = Timing.milliseconds(() -> {
      // Now lookup collection frequencies, this is p_x to termProxCounts p_xy
      termProxCounts.forEachEntry((term, frequency) -> {
        // skip query itself.
        if(query.contains(term)) return true;
          topTerms.add(new PMITerm(
              term,
              index.collectionFrequency(term),
              queryFrequency,
              frequency,
              collectionLength));
        return true;
      });
    });
    System.err.println("Scored " + termProxCounts.size() + " candidates in " + scoringTime + " ms.");

    for (PMITerm pmiTerm : topTerms.getSorted()) {
      System.out.printf("%-20s %1.4f\n", StrUtil.replaceUnicodeQuotes(pmiTerm.term), pmiTerm.pmi());
    }

  }
}

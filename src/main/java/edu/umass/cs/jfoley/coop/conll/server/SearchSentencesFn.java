package edu.umass.cs.jfoley.coop.conll.server;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.errors.NotHandledNow;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.AnyOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.general.NamespacedLabel;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class SearchSentencesFn extends IndexServerFn {
  public SearchSentencesFn(TermBasedIndexReader index) {
    super(index);
    // prime the tokenizer.
    CoopTokenizer.create();
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException {
    String field = input.get("field", "lemmas");
    String operation = input.get("operation", "or");
    int pageOffset = input.get("offset", 0);
    assert(pageOffset >= 0);
    int pageSize = input.get("count", 10);
    assert(pageSize > 1);

    CoopTokenizer tok = CoopTokenizer.create();
    CoopDoc document = tok.createDocument("query", input.getString("query"));
    List<String> terms = document.getTerms(field);

    List<NamespacedLabel> nl = new ArrayList<>();
    for (String term : terms) {
      nl.add(new NamespacedLabel(field, term));
    }

    List<PostingMover<Integer>> countFeatures = new ArrayList<>();
    for (Pair<NamespacedLabel, PostingMover<Integer>> kv : index.sentencesByTerms.getInBulk(nl)) {
      countFeatures.add(kv.getValue());
    }

    Mover mover;
    switch (operation) {
      case "or": mover = new AnyOfMover<>(countFeatures); break;
      case "and": mover = new AllOfMover<>(countFeatures); break;
      default: throw new NotHandledNow("operation", operation);
    }

    IntList output = new IntList();
    int totalHits = 0;
    for (; mover.hasNext(); mover.next()) {
      totalHits++;
      output.add(mover.currentKey());
    }

    //TODO IntList::slice, AChaiList::slice
    IntList pageHits = new IntList(ListFns.slice(output, pageOffset, pageOffset + pageSize));

    Parameters results = Parameters.create();
    results.put("totalHits", totalHits);
    results.put("queryTerms", terms);
    results.put("field", field);
    results.put("operation", operation);
    results.put("results", pullSentenceJSON(pageHits));
    return results;
  }
}

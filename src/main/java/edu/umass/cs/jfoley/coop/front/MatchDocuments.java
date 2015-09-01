package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.errors.NotHandledNow;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.AnyOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class MatchDocuments extends CoopIndexServerFn {
  protected MatchDocuments(CoopIndex index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException, SQLException {
    Parameters output = Parameters.create();
    String operation = input.get("operation", "AND");
    String termKind = input.get("termKind", "lemmas");
    int limit = input.get("limit", 200);

    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = tokenizer.createDocument("tmp", input.getString("query")).getTerms(termKind);
    output.put("queryTerms", query);

    List<PostingMover<PositionsList>> terms = new ArrayList<>();
    List<String> missingTerms = new ArrayList<>();

    for (String queryTerm : query) {
      PostingMover<PositionsList> x = index.getPositionsMover(termKind, queryTerm);
      if(x != null) {
        terms.add(x);
      } else {
        missingTerms.add(queryTerm);
      }
    }

    if(!missingTerms.isEmpty()) { output.put("missingTerms", missingTerms); }

    Mover operationMover;
    switch(operation) {
      case "AND":
        operationMover = new AllOfMover<>(terms);
        break;
      case "OR":
        operationMover = new AnyOfMover<>(terms);
        break;
      default:
        throw new NotHandledNow("operation", operation);
    }

    IntList hits = new IntList();
    for(operationMover.start(); !operationMover.isDone(); operationMover.next()) {
      if(hits.size() > limit) {
        break;
      }
      hits.add(operationMover.currentKey());
    }

    List<Parameters> results = new ArrayList<>();
    for (Pair<Integer, String> kv : index.lookupNames(hits)) {
      Parameters doc = Parameters.create();
      doc.put("id", kv.left);
      doc.put("name", kv.right);
      results.add(doc);
    }

    output.put("results", results);
    return output;
  }
}

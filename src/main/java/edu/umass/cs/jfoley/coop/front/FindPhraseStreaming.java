package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.collections.list.BitVector;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class FindPhraseStreaming extends CoopIndexServerFn {
  protected FindPhraseStreaming(IndexReader index) {
    super(index);
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

    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = tokenizer.createDocument("tmp", p.getString("query")).getTerms(termKind);

    output.put("queryTerms", query);
    if(query.isEmpty()) {
      return output;
    }

    List<String> missingTerms = new ArrayList<>();
    List<PostingMover<PositionsList>> termMovers = new ArrayList<>();
    List<Mover> nonNullMovers = new ArrayList<>();
    query.forEach((x) -> {
      // may have nulls
      PostingMover<PositionsList> tmover = index.getPositionsMover(x);
      if (tmover != null) {
        nonNullMovers.add(tmover);
      } else {
        missingTerms.add(x);
      }
      termMovers.add(tmover);
    });

    output.put("missingTerms", missingTerms);

    if(nonNullMovers.isEmpty()) {
      return output;
    }

    Mover andTerms = new AllOfMover<>(nonNullMovers);
    List<BitVector> vectors = new ArrayList<>();
    for (Mover nonNullMover : nonNullMovers) {
      vectors.add(new BitVector(2048)); // longest supported document
    }
    andTerms.execute((doc) -> {
      int vp = 0;
      for (int i = 0; i < termMovers.size(); i++) {
        PostingMover<PositionsList> termMover = termMovers.get(i);
        if (termMover == null) { continue; }
        BitVector vec = vectors.get(vp++);
        termMover.getCurrentPosting().fill(vec);
        vec.shiftLeft(-i);
      }
    });

    BitVector first = vectors.get(0);
    for (int i = 1; i < vectors.size(); i++) {
      first.and(vectors.get(i));
    }

    return null;
  }
}

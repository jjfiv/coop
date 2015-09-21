package edu.umass.cs.jfoley.coop.front.eval;

import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author jfoley
 */
public class LookupSingleTermMethod extends FindHitsMethod {

  private final PostingMover<PositionsList> mover;
  private final String term;
  private int termId;

  public LookupSingleTermMethod(Parameters input, Parameters output, TermPositionsIndex index) throws IOException {
    super(input, output);
    PostingMover<PositionsList> mover = null;
    this.termId = -1;
    if (input.isLong("termId")) {
      this.termId = input.getInt("termId");
      this.term = index.translateToTerm(termId);
    } else if (input.isString("term")) {
      this.term = input.getString("term");
      this.termId = index.translateFromTerm(term);
    } else throw new IllegalArgumentException("Missing argument term=\"the\" or termId=1, etc.");
    mover = index.getPositionsMover(termId);
    this.mover = mover;
  }

  @Override
  public ArrayList<DocumentResult<Integer>> compute() throws IOException {
    ArrayList<DocumentResult<Integer>> hits = new ArrayList<>();
    if (mover == null) return hits;
    mover.execute((docId) -> {
      PositionsList list = mover.getPosting(docId);
      for (int i = 0; i < list.size(); i++) {
        hits.add(new DocumentResult<>(docId, list.getPosition(i)));
      }
    });
    return hits;
  }

  @Override
  public int getPhraseWidth() {
    return 1;
  }

  @Override
  public boolean queryContains(int term) {
    return this.termId == term;
  }
}

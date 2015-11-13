package edu.umass.cs.jfoley.coop.front.eval;

import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.front.CoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class LookupSinglePhraseMethod extends FindHitsMethod {

  private final PostingMover<PositionsList> mover;
  private final List<String> terms;
  private IntList queryIds;


  public LookupSinglePhraseMethod(Parameters input, Parameters output, CoopIndex index) throws IOException {
    super(input, output);
    PhrasePositionsIndex entitiesIndex = index.getEntitiesIndex();
    int phraseId = input.getInt("phrase");
    this.mover = entitiesIndex.getPositionsMover(phraseId);
    queryIds = entitiesIndex.getPhraseVocab().getForward(phraseId);
    terms = index.getPositionsIndex("lemmas").translateToTerms(queryIds);

    output.put("phraseIds", queryIds);
    output.put("phraseTerms", terms);
  }

  @Override
  public List<DocumentResult<Integer>> compute() throws IOException {
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
    return queryIds.size();
  }

  @Override
  public boolean queryContains(int term) {
    return this.queryIds.containsInt(term);
  }
}

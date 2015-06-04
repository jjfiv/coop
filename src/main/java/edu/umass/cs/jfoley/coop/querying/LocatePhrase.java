package edu.umass.cs.jfoley.coop.querying;

import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.index.Index;
import edu.umass.cs.ciir.waltz.phrase.OrderedWindow;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains methods for locating terms or phrases in an index.
 * @author jfoley
 */
public class LocatePhrase {
  public static List<DocumentResult<Integer>> find(Index index, List<String> terms) {
    switch (terms.size()) {
      case 0: throw new IllegalArgumentException("Didn't specify any terms to search!");
      case 1: return findTermImpl(index, terms.get(0));
      default: return findPhraseImpl(index, terms);
    }
  }

  static List<DocumentResult<Integer>> findTermImpl(Index index, String term) {
    List<DocumentResult<Integer>> hits = new ArrayList<>();
    PostingMover<PositionsList> mover = index.getPositionsMover(term);

    for(; !mover.isDone(); mover.next()) {
      int doc = mover.currentKey();
      for (int pos : mover.getCurrentPosting()) {
        hits.add(new DocumentResult<>(doc, pos));
      }
    }

    return hits;
  }

  static List<DocumentResult<Integer>> findPhraseImpl(Index index, List<String> phraseTerms) {
    List<PostingMover<PositionsList>> phraseMovers = new ArrayList<>();
    for (String phraseTerm : phraseTerms) {
      phraseMovers.add(index.getPositionsMover(phraseTerm));
    }

    List<DocumentResult<Integer>> hits = new ArrayList<>();

    AllOfMover andMovement = new AllOfMover(phraseMovers);
    for(; !andMovement.isDone(); andMovement.next()) {
      List<PositionsIterator> positions = new ArrayList<>();
      for (PostingMover<PositionsList> phraseMover : phraseMovers) {
        positions.add(phraseMover.getCurrentPosting().getExtentsIterator());
      }

      for (int pos : OrderedWindow.findIter(positions, 1)) {
        hits.add(new DocumentResult<>(andMovement.currentKey(), pos));
      }
    }

    return hits;
  }
}

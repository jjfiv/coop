package edu.umass.cs.jfoley.coop.querying;

import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.index.Index;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.querying.eval.QueryEvalEngine;
import edu.umass.cs.jfoley.coop.querying.eval.QueryEvalNode;
import edu.umass.cs.jfoley.coop.querying.eval.nodes.FeatureQueryNode;
import edu.umass.cs.jfoley.coop.querying.eval.nodes.PhraseNode;

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

    //noinspection Convert2Diamond -- intellij is wrong about javac's inference capabilities :(
    QueryEvalEngine.EvaluateOneToMany(
        mover,
        new FeatureQueryNode<PositionsList>(mover),
        hits::add);

    return hits;
  }

  static List<DocumentResult<Integer>> findPhraseImpl(Index index, List<String> phraseTerms) {
    List<PostingMover<PositionsList>> phraseMovers = new ArrayList<>();
    List<QueryEvalNode<PositionsList>> features = new ArrayList<>();
    for (String phraseTerm : phraseTerms) {
      PostingMover<PositionsList> positionsMover = index.getPositionsMover(phraseTerm);
      phraseMovers.add(positionsMover);
      features.add(new FeatureQueryNode<>(positionsMover));
    }

    List<DocumentResult<Integer>> hits = new ArrayList<>();

    QueryEvalEngine.EvaluateOneToMany(
        new AllOfMover<>(phraseMovers),
        new PhraseNode(features),
        hits::add);

    return hits;
  }
}

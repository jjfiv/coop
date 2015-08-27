package edu.umass.cs.jfoley.coop.querying;

import ciir.jfoley.chai.fn.SinkFn;
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
    List<DocumentResult<Integer>> hits = new ArrayList<>();
    switch (terms.size()) {
      case 0: throw new IllegalArgumentException("Didn't specify any terms to search!");
      case 1: findTermImpl(index, terms.get(0), hits::add); break;
      default: findPhraseImpl(index, terms, hits::add); break;
    }
    return hits;
  }

  public static void find(Index index, List<String> terms, SinkFn<DocumentResult<Integer>> output) {
    switch (terms.size()) {
      case 0: throw new IllegalArgumentException("Didn't specify any terms to search!");
      case 1: findTermImpl(index, terms.get(0), output);
      default: findPhraseImpl(index, terms, output);
    }
  }

  static void findTermImpl(Index index, String term, SinkFn<DocumentResult<Integer>> output) {
    List<DocumentResult<Integer>> hits = new ArrayList<>();
    PostingMover<PositionsList> mover = index.getPositionsMover(term);

    if(mover == null) return;

    //noinspection Convert2Diamond -- intellij is wrong about javac's inference capabilities :(
    QueryEvalEngine.EvaluateOneToMany(
        mover,
        new FeatureQueryNode<PositionsList>(mover),
        output);
  }

  static void findPhraseImpl(Index index, List<String> phraseTerms, SinkFn<DocumentResult<Integer>> output) {
    List<PostingMover<PositionsList>> phraseMovers = new ArrayList<>();
    List<QueryEvalNode<PositionsList>> features = new ArrayList<>();
    for (String phraseTerm : phraseTerms) {
      PostingMover<PositionsList> positionsMover = index.getPositionsMover(phraseTerm);
      if(positionsMover == null) {
        System.err.println("Couldn't find: "+phraseTerm);
        continue;
      }
      phraseMovers.add(positionsMover);
      features.add(new FeatureQueryNode<>(positionsMover));
    }

    QueryEvalEngine.EvaluateOneToMany(
        new AllOfMover<>(phraseMovers),
        new PhraseNode(features),
        output);
  }
}

package edu.umass.cs.jfoley.coop.querying;

import ciir.jfoley.chai.fn.SinkFn;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.front.CoopIndex;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.querying.eval.QueryEvalEngine;
import edu.umass.cs.jfoley.coop.querying.eval.QueryEvalNode;
import edu.umass.cs.jfoley.coop.querying.eval.nodes.FeatureQueryNode;
import edu.umass.cs.jfoley.coop.querying.eval.nodes.PhraseNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains methods for locating terms or phrases in an index.
 * @author jfoley
 */
public class LocatePhrase {
  public static List<DocumentResult<Integer>> find(CoopIndex index, String termKind, List<String> terms) throws IOException {
    List<DocumentResult<Integer>> hits = new ArrayList<>();
    switch (terms.size()) {
      case 0: throw new IllegalArgumentException("Didn't specify any terms to search!");
      case 1: findTermImpl(index, termKind, terms.get(0), hits::add); break;
      default: findPhraseImpl(index, termKind, terms, hits::add); break;
    }
    return hits;
  }

  public static void find(CoopIndex index, String termKind, List<String> terms, SinkFn<DocumentResult<Integer>> output) throws IOException {
    switch (terms.size()) {
      case 0: throw new IllegalArgumentException("Didn't specify any terms to search!");
      case 1: findTermImpl(index, termKind, terms.get(0), output);
      default: findPhraseImpl(index, termKind, terms, output);
    }
  }

  static void findTermImpl(CoopIndex index, String termKind, String term, SinkFn<DocumentResult<Integer>> output) throws IOException {
    PostingMover<PositionsList> mover = index.getPositionsMover(termKind, term);
    if(mover == null) {
      System.err.println("Couldn't find: "+termKind+":"+term);
    }

    if(mover == null) return;

    //noinspection Convert2Diamond -- intellij is wrong about javac's inference capabilities :(
    QueryEvalEngine.EvaluateOneToMany(
        mover,
        new FeatureQueryNode<PositionsList>(mover),
        output);
  }

  static void findPhraseImpl(CoopIndex index, String termKind, List<String> phraseTerms, SinkFn<DocumentResult<Integer>> output) throws IOException {
    List<PostingMover<PositionsList>> phraseMovers = new ArrayList<>();
    List<QueryEvalNode<PositionsList>> features = new ArrayList<>();
    for (String phraseTerm : phraseTerms) {
      PostingMover<PositionsList> positionsMover = index.getPositionsMover(termKind, phraseTerm);
      if(positionsMover == null) {
        System.err.println("Couldn't find: "+termKind+":"+phraseTerm);
        continue;
      }
      phraseMovers.add(positionsMover);
      features.add(new FeatureQueryNode<>(positionsMover));
    }

    System.err.println("phrase: "+phraseMovers);

    QueryEvalEngine.EvaluateOneToMany(
        new AllOfMover<>(phraseMovers),
        new PhraseNode(features),
        output);
  }
}

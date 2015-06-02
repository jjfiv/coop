package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.dociter.movement.AllOfMover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.phrase.OrderedWindow;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsIterator;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.index.VocabReader;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.tokenize.Tokenizer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley.
 */
public class PhraseFinder extends AppFunction {
  @Override
  public String getName() {
    return "phrase-finder";
  }

  @Override
  public String getHelpString() {
    return makeHelpStr(
        "index", "path to VocabReader index.",
        "query", "a term or phrase query");
  }

  @Override
  public void run(Parameters p, PrintStream output) throws Exception {
    try (VocabReader index = new VocabReader(new Directory(p.getString("index")))) {
      Tokenizer tokenizer = new TagTokenizer();
      List<String> query = tokenizer.tokenize(p.getString("query")).terms;
      for (DocumentAndPosition hit : easyFindPhrase(index, query)) {
        System.out.println(hit.documentId+" "+hit.matchPosition);
      }
    }
  }

  public static List<DocumentAndPosition> easyFindPhrase(VocabReader index, List<String> terms) {
    switch (terms.size()) {
      case 0: throw new IllegalArgumentException("Didn't specify any terms to search!");
      case 1: return findTerm(index, terms.get(0));
      default: return findPhrase(index, terms);
    }
  }

  private static List<DocumentAndPosition> findTerm(VocabReader index, String term) {
    List<DocumentAndPosition> hits = new ArrayList<>();
    PostingMover<PositionsList> mover = index.getPositionsMover(term);

    for(; !mover.isDone(); mover.next()) {
      int doc = mover.currentKey();
      for (int pos : mover.getCurrentPosting().toList()) {
        hits.add(new DocumentAndPosition(doc, pos));
      }
    }

    return hits;
  }

  private static List<DocumentAndPosition> findPhrase(VocabReader index, List<String> phraseTerms) {
    List<PostingMover<PositionsList>> phraseMovers = new ArrayList<>();
    for (String phraseTerm : phraseTerms) {
      phraseMovers.add(index.getPositionsMover(phraseTerm));
    }

    List<DocumentAndPosition> hits = new ArrayList<>();

    AllOfMover andMovement = new AllOfMover(phraseMovers);
    for(; !andMovement.isDone(); andMovement.next()) {
      List<PositionsIterator> positions = new ArrayList<>();
      for (PostingMover<PositionsList> phraseMover : phraseMovers) {
        positions.add(phraseMover.getCurrentPosting().getExtentsIterator());
      }

      for (int pos : OrderedWindow.findIter(positions, 1)) {
        hits.add(new DocumentAndPosition(andMovement.currentKey(), pos));
      }
    }

    return hits;
  }

}

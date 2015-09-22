package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsWriter;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;

/**
 * @author jfoley
 */
public class ExtractNames {
  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("robust.ints"));
    IntCoopIndex index = new IntCoopIndex(new Directory("dbpedia.ints"));

    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();

    System.err.println("Total: " + index.names.size());
    int count = IntMath.fromLong(index.names.size());

    Debouncer msg = new Debouncer(500);

    int N = 20;
    PhraseDetector detector = index.loadPhraseDetector(N, target);
    // Vocabulary loaded:

    // Now, see NERIndex
    ExtractNames234.CorpusTagger tagger = new ExtractNames234.CorpusTagger(detector, target.getCorpus());

    Debouncer msg2 = new Debouncer(2000);
    try (PhraseHitsWriter writer = new PhraseHitsWriter(new Directory("foo-bar"), "dbpedia")) {
      tagger.tag(msg2, (phraseId, docId, hitStart, hitSize, terms) -> {
        writer.onPhraseHit(phraseId, docId, hitStart, hitSize, IntList.clone(terms, hitStart, hitSize));
      });
    } // phrase-hits-writer
  }
}

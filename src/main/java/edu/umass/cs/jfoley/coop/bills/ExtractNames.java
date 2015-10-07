package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsWriter;

import java.io.IOException;

/**
 * @author jfoley
 */
public class ExtractNames {
  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("/mnt/scratch/jfoley/clue12a.sdm.ints"));
    IntCoopIndex index = new IntCoopIndex(new Directory("dbpedia.ints"));

    int N = 10;
    PhraseDetector detector = index.loadPhraseDetector(N, target);

    // Now, see NERIndex
    ExtractNames234.CorpusTagger tagger = new ExtractNames234.CorpusTagger(detector, target.getCorpus());

    Debouncer msg2 = new Debouncer(2000);
    Directory output = target.baseDir;
    //output = new Directory("test.foo");
    try (PhraseHitsWriter writer = new PhraseHitsWriter(output, "dbpedia")) {
      tagger.tag(msg2, (phraseId, docId, hitStart, hitSize, terms) -> {
        IntList data_found = IntList.clone(terms, hitStart, hitSize);
        /*
        try {
          System.err.println("phraseId: "+phraseId);
          IntList data = new IntList(target.corpus.getSlice(docId, hitStart, hitSize));
          System.err.println("Found: "+target.translateToTerms(data));
          System.err.println("Links: "+index.names.getForward(phraseId-2)+" "+index.names.getForward(phraseId-1)+" "+index.names.getForward(phraseId)+" "+index.names.getForward(phraseId+1)+" "+index.names.getForward(phraseId+2));
          System.err.println(data+" === "+data_found);
          assert(Objects.equals(data, data_found));
        } catch (IOException e) {
          e.printStackTrace();
        }
        */
        writer.onPhraseHit(phraseId, docId, hitStart, hitSize, data_found);
      });
    } // phrase-hits-writer
  }
}

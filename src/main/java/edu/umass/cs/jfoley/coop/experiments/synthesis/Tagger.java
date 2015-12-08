package edu.umass.cs.jfoley.coop.experiments.synthesis;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.sys.PostingsConfig;
import edu.umass.cs.ciir.waltz.sys.counts.CountMetadata;
import edu.umass.cs.ciir.waltz.sys.positions.PIndexWriter;
import edu.umass.cs.jfoley.coop.bills.ExtractNames234;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import gnu.trove.map.hash.TIntIntHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.Comparator;

/**
 * @author jfoley
 */
public class Tagger {

  public static PostingsConfig<Integer, Integer> phraseIdToCounts = new PostingsConfig<>(
      FixedSize.ints, FixedSize.ints, Comparator.naturalOrder(), new CountMetadata()
  );

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    IntCoopIndex target = new IntCoopIndex(new Directory(argp.get("target", "/mnt/scratch3/jfoley/clue12a.sdm.ints")));
    IntCoopIndex index = new IntCoopIndex(new Directory("/mnt/scratch3/jfoley/dbpedia.ints"));

    int N = 10;
    PhraseDetector detector = index.loadPhraseDetector(N, target);

    Directory output = target.baseDir;
    //output = new Directory("test.foo");
    try (PIndexWriter<Integer, Integer> entcounts = phraseIdToCounts.getWriter(output, "entcounts")) {

      ExtractNames234.CorpusProcessor processor = new ExtractNames234.CorpusProcessor(target.getCorpus());

      Debouncer msg = new Debouncer();
      StreamingStats hitsPerDoc = new StreamingStats();
      processor.run((docId, doc) -> {
        TIntIntHashMap bagOfPhrases = new TIntIntHashMap();
        int hitcount = detector.match(doc, (phraseId, hitStart, hitSize) -> {
          bagOfPhrases.adjustOrPutValue(phraseId, 1, 1);
        });
        hitsPerDoc.push(hitcount);

        // add to count writer
        bagOfPhrases.forEachEntry((phraseId, count) -> {
          entcounts.add(phraseId, docId, count);
          return true;
        });
        // add length, hardcoded as -1:
        entcounts.add(-1, docId, hitcount);

        if (msg.ready()) {
          System.out.println("NERing documents at: " + msg.estimate(processor.getCompleted(), processor.ND));
          System.out.println("NERing documents at terms/s: " + msg.estimate(processor.getTermsCompleted()));
          System.out.println("hitsPerDoc: " + hitsPerDoc);
        }
      });


    } // phrase-hits-writer
  }
}

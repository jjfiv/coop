package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsWriter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jfoley
 */
public class ExtractNames234 {

  public static class CorpusTagger {
    final PhraseDetector detector;
    final IntVocabBuilder.IntVocabReader corpus;

    public CorpusTagger(PhraseDetector detector, IntVocabBuilder.IntVocabReader corpus) {
      this.detector = detector;
      this.corpus = corpus;
    }

    public interface TagListener {
      void onTag(int doc, int start, int size, int[] terms);
    }
    public void tag(Debouncer msg, TagListener callback) throws IOException {
      CorpusProcessor processor = new CorpusProcessor(corpus);

      StreamingStats hitsPerDoc = new StreamingStats();
      processor.run((docId, doc) -> {
        int hitcount = detector.match(doc, (hitStart, hitSize) -> {
          callback.onTag(docId, hitStart, hitSize, doc);
          if (msg != null && msg.ready()) {
            System.out.println("NERing documents at: " + msg.estimate(processor.completed.get(), processor.ND));
            System.out.println("NERing documents at terms/s: " + msg.estimate(processor.termsCompleted.get()));
            System.out.println("hitsPerDoc: " + hitsPerDoc);
          }
        });
        hitsPerDoc.push(hitcount);
      });

      System.err.println(hitsPerDoc);
    }
  }

  public static class CorpusProcessor {
    public final IntVocabBuilder.IntVocabReader corpus;
    int ND;
    AtomicInteger completed = new AtomicInteger(0);
    AtomicLong termsCompleted = new AtomicLong(0);

    public CorpusProcessor(IntVocabBuilder.IntVocabReader corpus) throws IOException {
      this.corpus = corpus;
      this.ND = corpus.numberOfDocuments();
    }

    public interface DocumentListener {
      void handleDocument(int doc, int[] data);
    }
    public void run(DocumentListener listener) throws IOException {
      for (int docId = 0; docId < ND; docId++) {
        int[] doc = corpus.getDocument(docId);
        listener.handleDocument(docId, doc);
        completed.incrementAndGet();
        termsCompleted.addAndGet(doc.length);
      }
    }
  }

  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("robust.ints"));
    long start, end;

    // load up precomputed queries:
    start = System.currentTimeMillis();
    PhraseDetector detector = PhraseDetector.loadFromTextFile(20, target.baseDir.child("dbpedia.titles.intq.gz"));
    end = System.currentTimeMillis();
    System.out.println("loading titles: " + (end - start) + "ms.");

    CorpusTagger tagger = new CorpusTagger(detector, target.getCorpus());

    Debouncer msg = new Debouncer(2000);
    try (PhraseHitsWriter writer = new PhraseHitsWriter(target.baseDir, "entities")) {
      tagger.tag(msg, (docId, hitStart, hitSize, terms) -> {
        writer.onPhraseHit(docId, hitStart, hitSize, IntList.clone(terms, hitStart, hitSize));
      });
    } // phrase-hits-writer
  } // main
}

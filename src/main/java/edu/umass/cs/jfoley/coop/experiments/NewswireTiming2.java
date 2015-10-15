package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.FixedSlidingWindow;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.DocumentSource;
import org.lemurproject.galago.core.parse.DocumentStreamParser;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jfoley
 */
public class NewswireTiming2 {
  public interface StrPhraseMatchListener {
    void onPhraseMatch(int phraseId, int position, int size);
  }
  public static class StrPhraseDetector {
    public ArrayList<HashMap<List<String>, Integer>> matchingBySize;
    public int N;

    public StrPhraseDetector(int N) {
      this.N = N;
      this.matchingBySize = new ArrayList<>();
      for (int i = 0; i < N; i++) {
        matchingBySize.add(new HashMap<>());
      }
    }

    public void addPattern(List<String> data, int id) {
      int n = data.size()-1;
      if(n < 0 || n >= N) return;
      // use the first matching if duplicate patterns exist:
      matchingBySize.get(n).putIfAbsent(data, id);
    }

    public Integer getMatch(List<String> query) {
      int n = query.size()-1;
      if(n < 0 || n >= N) return null;
      return matchingBySize.get(n).get(query);
    }

    public boolean matches(List<String> query) {
      return getMatch(query) != null;
    }


    public int match(List<String> data, StrPhraseMatchListener handler) {
      // create circular buffers for this document:
      ArrayList<FixedSlidingWindow<String>> patternBuffers = new ArrayList<>(N);
      for (int i = 0; i < N; i++) {
        patternBuffers.add(new FixedSlidingWindow<>(i+1));
      }

      int hits = 0;
      for (int position = 0; position < data.size(); position++) {
        String term = data.get(position);
        // tag backwards, so that we get the longest/earliest patterns first
        for (int i = patternBuffers.size() - 1; i >= 0; i--) {
          FixedSlidingWindow<String> buffer = patternBuffers.get(i);
          buffer.add(term);
          if (buffer.full()) {
            Integer match = getMatch(buffer);
            if(match != null) {
              handler.onPhraseMatch(match, position - i, i + 1);
              hits++;
            }
          }
        }
      }
      return hits;
    }

    @Override
    public String toString() {
      return ListFns.map(matchingBySize, HashMap::size).toString();
    }
  }

  public static StrPhraseDetector loadPhraseDetector(int N) throws IOException {
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read("dbpedia.ints"));
    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();
    IdMaps.Reader<String> names = dbpedia.getNames();
    System.err.println("Total: " + names.size());
    int count = IntMath.fromLong(names.size());
    Debouncer msg = new Debouncer(500);
    StrPhraseDetector detector = new StrPhraseDetector(N);

    int docNameIndex = 0;
    int ND = dbpedia.getCorpus().numberOfDocuments();
    for (Pair<Integer,String> pair : names.items()) {
      int phraseId = pair.left;
      String name = pair.right;


      docNameIndex++;
      String text = IntCoopIndex.parseDBPediaTitle(name);
      List<String> query = tokenizer.tokenize(text).terms;
      int size = query.size();
      if(size == 0 || size > N) continue;
      detector.addPattern(query, phraseId);

      assert(pair.left < ND);

      if(msg.ready()) {
        System.err.println(text);
        //System.err.println(getDocument(phraseId));
        System.err.println(query);
        System.err.println(msg.estimate(docNameIndex, count));
        System.err.println(detector);
      }
    }
    return detector;
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    TagTokenizer tok = new TagTokenizer();
    // warmup phrasedet.
    int N = 10;
    StrPhraseDetector matcher = loadPhraseDetector(N);

    List<DocumentSplit> documentSplits = DocumentSource.processDirectory(new File(argp.get("input", "/mnt/scratch/jfoley/robust04raw")), argp);

    StreamingStats parsingTime = new StreamingStats();
    StreamingStats recognizeTime = new StreamingStats();
    final AtomicLong numQueries = new AtomicLong(0);

    int ND = 20000;
    int count = 0;
    //StreamingStats galagoTime = new StreamingStats();
    for (DocumentSplit documentSplit : documentSplits) {
      DocumentStreamParser parser = DocumentStreamParser.create(documentSplit, argp);

      Debouncer msg = new Debouncer(1000);
      long st, et;
      while (true) {
        st = System.nanoTime();
        Document doc = parser.nextDocument();
        if (doc == null) break;
        et = System.nanoTime();
        parsingTime.push((et - st) / 1e9);

        st = System.nanoTime();
        tok.tokenize(doc);
        matcher.match(doc.terms, (phraseId, position, size) -> numQueries.incrementAndGet());
        et = System.nanoTime();

        recognizeTime.push((et - st) / 1e9);

        if(msg.ready()) {
          System.err.println(msg.estimate(count, ND));
        }

        if(count++ >= ND) break;
      }

      if(count >= ND) break;
    }

    System.err.println("parsingTime: "+parsingTime);
    System.err.println("mysys.text.ner: "+recognizeTime);
    System.err.println("numQueries: "+numQueries.get());
  }
}


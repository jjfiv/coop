package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.streaming.StreamingPostingBuilder;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.DocumentSource;
import org.lemurproject.galago.core.parse.DocumentStreamParser;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class TextCountIndexer {

  public interface TermCallback {
    void add(String term, int doc, int count);
  }

  public static void main(String[] args) throws IOException {
    System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "10");

    Directory output = new Directory("test_speed.index");
    Parameters argp = Parameters.create();

    Debouncer msg = new Debouncer(1000);

    long startTime = System.currentTimeMillis();
    try (StreamingPostingBuilder<String, Integer> builder = new StreamingPostingBuilder<>( CharsetCoders.utf8, VarUInt.instance, GalagoIO.getRawIOMapWriter(output.childPath("sort-based-counts")))) {

      TagTokenizer tok = new TagTokenizer();
      int i = 0;
      final int TOTAL_DOCS = 1000000;
      for (DocumentSplit split : DocumentSource.processFile(new File("/mnt/scratch/jfoley/robust04raw/"), argp)) {
        try (DocumentStreamParser p = DocumentStreamParser.create(split, argp)) {
          while (true) {
            Document doc = p.nextDocument();
            if (doc == null) break;
            if (msg.ready()) {
              System.err.println("# " + doc.name + " " + i);
              System.err.println("# " + msg.estimate(i, TOTAL_DOCS));
            }

            tok.tokenize(doc);
            HashMap<String, Integer> counts = new HashMap<>();
            for (String term : doc.terms) {
              MapFns.addOrIncrement(counts, term, 1);
            }
            for (Map.Entry<String, Integer> kv : counts.entrySet()) {
              builder.add(kv.getKey(), i, kv.getValue());
            }
            i++;
            if(i > TOTAL_DOCS) break;
          }
        }
        if(i > TOTAL_DOCS) break;
      }

      long endParsingTime = System.currentTimeMillis();
      System.out.println("Total parsing time: " + (endParsingTime - startTime) + "ms.");
    }
    long endTime = System.currentTimeMillis();
    System.out.println("Total time: " + (endTime - startTime) + "ms.");
    // 27.6s i>=50
    // 17.2s i>=50 with threaded Sorter
    // parse: 189,280ms, total: 239,167ms i>=500
  }
}


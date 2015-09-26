package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.bills.IntCorpusPositionIndexer;
import edu.umass.cs.jfoley.coop.bills.IntVocabBuilder;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;

/**
 * @author jfoley
 */
public class IntCorpusPIndex {

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    //String path = argp.get("input", "/mnt/scratch/jfoley/inex-page-djvu.ints");
    String path = argp.get("input", "dbpedia.ints");
    Directory input = Directory.Read(path);
    Directory output = Directory.Read(argp.get("output", path));

    long startTime = System.currentTimeMillis();
    Debouncer msg = new Debouncer(5000);

    int emptyDocuments = 0;
    try (IntVocabBuilder.IntVocabReader reader = new IntVocabBuilder.IntVocabReader(input);
        IntCorpusPositionIndexer indexer = new IntCorpusPositionIndexer(output)) {
      int numDocuments = reader.numberOfDocuments();
      long globalPosition = 0;
      long numTerms = reader.numberOfTermOccurrences();

      for (int i = 0; i < numDocuments; i++) {
        int[] terms = reader.getDocument(i);
        if(terms.length == 0) {
          emptyDocuments++;
          continue;
        }
        indexer.add(i, terms);
        globalPosition += terms.length;

        if (msg.ready()) {
          System.out.println("Progress: " + globalPosition + " / " + numTerms+" empty: "+emptyDocuments);
          System.out.println(msg.estimate(globalPosition, numTerms));
          System.out.println();
        }
      }
    }
    long endTime = System.currentTimeMillis();

    System.out.println("Total Time: "+(endTime - startTime));
    // Progress: 237809552 / 252359881
    // 3642915.9 items/s  4.0 seconds left, 94.2% complete.
    // Total Time: 169016

    // bills
    // Progress: 324483348 / 334913031
    // 2179993.7 items/s  4.8 seconds left, 96.9% complete.
    // Total Time: 181479
  }
}

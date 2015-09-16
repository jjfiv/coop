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
    Directory input = Directory.Read(argp.get("input", "dbpedia.ints"));
    Directory output = new Directory(argp.get("output", "dbpedia.ints"));
    //Directory input = Directory.Read(argp.get("input", "/mnt/scratch/jfoley/int-corpora/robust.ints"));

    long startTime = System.currentTimeMillis();
    Debouncer msg = new Debouncer(5000);

    try (IntVocabBuilder.IntVocabReader reader = new IntVocabBuilder.IntVocabReader(input);
        IntCorpusPositionIndexer indexer = new IntCorpusPositionIndexer(output)) {
      int numDocuments = reader.numberOfDocuments();
      long globalPosition = 0;
      long numTerms = reader.numberOfTermOccurrences();

      for (int i = 0; i < numDocuments; i++) {
        int[] terms = reader.getDocument(i);
        if(terms.length == 0) {
          System.err.println("Empty-doc: "+i);
          continue;
        }
        indexer.add(i, terms);
        globalPosition += terms.length;

        if (msg.ready()) {
          System.out.println("Progress: " + globalPosition + " / " + numTerms);
          System.out.println(msg.estimate(globalPosition, numTerms));
          System.out.println();
        }
      }
    }
    long endTime = System.currentTimeMillis();

    System.out.println("Total Time: "+(endTime - startTime));
    // Progress: 249670890 / 252359881
    // 1414862.5 items/s  1.9 seconds left, 98.9% complete.
    // Total Time: 242472

    // bills
    // Progress: 324483348 / 334913031
    // 2179993.7 items/s  4.8 seconds left, 96.9% complete.
    // Total Time: 181479
  }
}

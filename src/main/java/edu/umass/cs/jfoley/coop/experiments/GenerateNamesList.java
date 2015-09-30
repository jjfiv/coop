package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author jfoley.
 */
public class GenerateNamesList {
  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    IntCoopIndex index = new IntCoopIndex(Directory.Read(argp.get("index", "dbpedia.ints")));

    System.err.println("Total: " + index.getNames().size());
    int count = IntMath.fromLong(index.getNames().size());
    Debouncer msg = new Debouncer(500);

    try (PrintWriter output = IO.openPrintWriter(argp.get("output", "dbpedia.names.gz"))) {
      int docNameIndex = 0;
      for (Pair<Integer, String> pair : index.getNames().items()) {
        int phraseId = pair.left;
        String name = pair.right;
        output.println(name);
        docNameIndex++;
        String text = IntCoopIndex.parseDBPediaTitle(name);
        if (msg.ready()) {
          System.err.println(text);
          //System.err.println(getDocument(phraseId));
          System.err.println(phraseId + " " + name);
          System.err.println(msg.estimate(docNameIndex, count));
        }
      }
    }
  }
}

package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.io.LinesIterable;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.HashSet;

/**
 * @author jfoley
 */
public class CollectFACCForSubset {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    HashSet<String> validDocs = new HashSet<>(LinesIterable.fromFile(argp.getString("names")).slurp());

    for (String line : LinesIterable.fromFile(argp.getString("input"))) {
      String[] row = line.split("\t");
      String docId = row[0];
      if(!validDocs.contains(docId)) {
        continue;
      }
      String fbId = row[7];
      System.out.println(docId+"\t"+fbId);
    }
  }
}

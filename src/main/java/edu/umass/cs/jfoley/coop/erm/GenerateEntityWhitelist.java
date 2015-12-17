package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.io.IO;
import edu.umass.cs.jfoley.coop.entityco.PMIRankingExperiment;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * @author jfoley
 */
public class GenerateEntityWhitelist {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.create();
    LocalRetrieval localRetrieval = PMIRankingExperiment.openJeffWiki(argp);

    HashSet<String> jeffNames = new HashSet<>();
    localRetrieval.getIndex().getNamesIterator().forEachData(jeffNames::add);

    HashSet<String> dbpediaNames = new HashSet<>();
    LocalRetrieval dbpedia = new LocalRetrieval("/mnt/scratch3/jfoley/dbpedia.galago");
    dbpedia.getIndex().getNamesIterator().forEachData(dbpediaNames::add);

    List<HashSet<String>> toIntersect = new ArrayList<>(Arrays.asList(jeffNames, dbpediaNames));
    toIntersect.sort((lhs, rhs) -> Integer.compare(lhs.size(), rhs.size()));

    HashSet<String> smaller = toIntersect.get(0);
    HashSet<String> larger = toIntersect.get(1);

    try (PrintWriter validKB = IO.openPrintWriter("validKB.names.gz")) {
      for (String name : smaller) {
        if (larger.contains(name)) {
          validKB.println(name);
        }
      }
    }

  }
}

package edu.umass.cs.jfoley.coop.bdaat;

import ciir.jfoley.chai.io.FS;
import gnu.trove.list.array.TDoubleArrayList;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author jfoley
 */
public class CollectResults {
  public static void main(String[] args) throws IOException {
    for (File file : FS.listDirectory("coop")) {
      if(file.getName().endsWith(".json")) {
        Parameters experimentResults = Parameters.parseFile(file);
        List<String> queries = experimentResults.getList("queries", String.class);
        int numQueries = queries.size();
        int querySize = queries.get(0).split("\\s+").length;
        TDoubleArrayList timesInNS = new TDoubleArrayList();
        for (long time : experimentResults.getAsList("times", Long.class)) {
          timesInNS.add(time);
        }
        System.out.printf("%s\t%d\t%d", file.getName(), numQueries, querySize);
        System.out.printf("\t%g",(timesInNS.sum()/(double) timesInNS.size()));
        System.out.println();
      }
    }

  }
}

package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.entityco.EntityJudgedQuery;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Generate "topdocs" if needed.
 * @author jfoley
 */
public class RunQueries {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("index", "robust.ints")));
    List<Parameters> queries = argp.getList("queries", Parameters.class);
    String output = argp.get("output", "test.trecrun");
    int requested = argp.get("requested", 1000);

    try (PrintWriter pw = IO.openPrintWriter(output)) {
      for (Parameters jquery : queries) {
        EntityJudgedQuery query = EntityJudgedQuery.fromJSON(jquery);

        System.err.println(query.qid + " " + query.getText());
        List<ScoredDocument> topQL = target.searchQL(query.getText(), "dirichlet", requested);

        for (ScoredDocument scoredDocument : topQL) {
          pw.println(scoredDocument.toTRECformat(query.qid, "QL-nostem"));
        }
      }
    }

  }
}

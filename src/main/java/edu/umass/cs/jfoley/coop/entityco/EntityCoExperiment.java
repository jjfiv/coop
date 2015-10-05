package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley
 */
public class EntityCoExperiment {

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/robust04.json")));
    Collections.sort(queries, (lhs, rhs) -> lhs.qid.compareTo(rhs.qid));
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "dbpedia.ints")));
    //IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", "robust.ints")));

    String smoothing = argp.get("smoothing", "linear");

    try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", "dbpedia.ql."+smoothing+".trecrun"))) {
      for (EntityJudgedQuery query : queries) {
        String qid = query.qid;
        //long start = System.currentTimeMillis();
        //List<ScoredDocument> docs = target.searchQL(query.text, "dirichlet", 1000);
        //long end = System.currentTimeMillis();
        System.out.println(qid+" "+query.text);

        List<ScoredDocument> entities = dbpedia.searchQL(query.text, smoothing, 1000);

        for (ScoredDocument entity : entities) {
          trecrun.println(entity.toTRECformat(qid, "jfoley"));
        }
      }
    }
  }
}

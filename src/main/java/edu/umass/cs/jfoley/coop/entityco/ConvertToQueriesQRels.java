package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.io.IO;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class ConvertToQueriesQRels {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/books-human.json")));

    try (PrintWriter queryTSV = IO.openPrintWriter("coop/ecir2016runs/queries/books-human.tsv");
         PrintWriter qrel = IO.openPrintWriter("coop/ecir2016runs/qrels/books-human.ent.qrel")) {
      for (EntityJudgedQuery query : queries) {
        queryTSV.println(query.qid+"\t"+query.text);

        for (Map.Entry<String, Double> kv : query.judgments.entrySet()) {
          qrel.println(query.qid+" Q0 "+kv.getKey()+" "+kv.getValue());
        }
      }
    }
  }
}

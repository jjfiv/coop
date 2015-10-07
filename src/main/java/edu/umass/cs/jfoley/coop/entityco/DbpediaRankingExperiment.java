package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
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
public class DbpediaRankingExperiment {

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    String dataset = "robust04";
    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/"+dataset+".json")));
    Collections.sort(queries, (lhs, rhs) -> lhs.qid.compareTo(rhs.qid));
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "dbpedia.ints")));
    //IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", "robust.ints")));

    String smoothing = argp.get("smoothing", "dirichlet");

    try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", dataset+".dbpedia.ql."+smoothing+".trecrun"))) {
      for (EntityJudgedQuery query : queries) {
        String qid = query.qid;
        //long start = System.currentTimeMillis();
        //List<ScoredDocument> docs = target.searchQL(query.text, "dirichlet", 1000);
        //long end = System.currentTimeMillis();
        System.out.println(qid+" "+query.text);

        List<ScoredDocument> entities = dbpedia.searchQL(query.text, smoothing, 1000);

        for (ScoredDocument entity : entities) {
          if(entity.rank < 4) {
            System.out.println("\t"+entity.documentName+" "+entity.score);
            System.out.println("\t\t"+ StrUtil.join(dbpedia.translateToTerms(new IntList(dbpedia.getCorpus().getDocument((int) entity.document)))));
          }
          trecrun.println(entity.toTRECformat(qid, "jfoley"));
        }
      }
    }
  }
}

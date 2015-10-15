package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.io.IO;
import org.lemurproject.galago.core.eval.EvalDoc;
import org.lemurproject.galago.core.eval.QuerySetJudgments;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.core.retrieval.ScoredDocument;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author jfoley
 */
public class DropQueriesFromTrecrun {
  public static void main(String[] args) throws IOException {
    QuerySetJudgments qrels = new QuerySetJudgments("coop/ecir2016runs/qrels/robust04.x.ent.qrel", false, false);
    QuerySetResults input = new QuerySetResults("coop/schuhmacher/models_baselines_rewq/WikiSDM.txt.ranking.qrel");

    try (PrintWriter out = IO.openPrintWriter("output/robust04/el-wikisdm.trecrun")) {
      for (String qid : qrels.keySet()) {
        for (EvalDoc evalDoc : input.get(qid).getIterator()) {
          ScoredDocument sdoc = new ScoredDocument(evalDoc.getName(), evalDoc.getRank(), evalDoc.getScore());
          out.println(sdoc.toTRECformat(qid, "el-wikisdm"));
        }
      }
    }

  }
}

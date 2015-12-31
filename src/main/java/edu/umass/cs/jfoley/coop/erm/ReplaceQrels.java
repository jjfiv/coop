package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.umass.edu.learning.DenseDataPoint;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.eval.QuerySetJudgments;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author jfoley
 */
public class ReplaceQrels {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    boolean clueweb = argp.get("clueweb", true);

    QuerySetJudgments qrels = new QuerySetJudgments(argp.get("judgments", clueweb ? "clue12.mturk.qrel" : "robust.mturk.qrel"));
    String ranklibInput = argp.get("input", "coop/schuhmacher/text_graph/ranklib/data_clueweb/"+ (clueweb ? "clue12-sdm20-gsqueries.alldata.entities" : "rewq.alldata.entities"));
    String ranklibOutput = argp.get("output", "coop/schuhmacher/text_graph/ranklib/data_clueweb/"+(clueweb ? "rewq-all.clue12.fixed.ranklib" : "rewq-all.robust.fixed.ranklib"));

    TObjectIntHashMap<String> changesByQuery = new TObjectIntHashMap<>();
    try (LinesIterable input = LinesIterable.fromFile(ranklibInput);
         PrintWriter output = IO.openPrintWriter(ranklibOutput)) {
      for (String instance : input) {
        DenseDataPoint pt = new DenseDataPoint(instance);
        String docName = StrUtil.takeAfter(pt.getDescription(), "#").trim();
        String qid = pt.getID();

        //System.out.println(pt);
        int judgment = qrels.get(qid).get(docName);
        int oldJ = pt.getLabel()-1 > 0 ? 1 : 0;
        //System.out.println(qid+": "+docName+" "+oldJ+" "+judgment);

        if(oldJ != judgment) {
          changesByQuery.adjustOrPutValue(qid, 1, 1);
        }

        // update judgment:
        pt.setLabel(judgment);
        // save to file:
        output.println(pt);
      }
    }

    System.out.println(changesByQuery);
  }
}

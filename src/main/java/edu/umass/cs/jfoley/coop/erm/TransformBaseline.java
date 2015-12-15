package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.io.IO;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.lists.Ranked;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class TransformBaseline {
  public static void main(String[] args) throws IOException {

    Map<String, String> conversions = new HashMap<>();

    conversions.put("robust04.rewq.full.trecrun", "coop/schuhmacher/text_graph/ranklib/models_rewq/ALL.txt.ranking.qrel");
    conversions.put("robust04.rewq.wikisdm.trecrun", "coop/schuhmacher/text_graph/ranklib/models_ft_rewq/ft-WikiSDM.txt.ranking.qrel");
    conversions.put("clue12.rewq.full.trecrun", "coop/schuhmacher/text_graph/ranklib/models_clue12/ALL.txt.ranking.qrel");
    conversions.put("clue12.rewq.wikisdm.trecrun", "coop/schuhmacher/text_graph/ranklib/models_ft_clue/ft-WikiSDM.txt.ranking.qrel");


    conversions.forEach((output, input) -> {
      try (PrintWriter trecrun = IO.openPrintWriter(output)) {
        QuerySetResults qres = new QuerySetResults(input);
        for (String qid : qres.getQueryIterator()) {
          List<ScoredDocument> sdocs = new ArrayList<>();
          qres.get(qid).forEach(x -> {
            sdocs.add(new ScoredDocument(x.getName(), -1, x.getScore()));
          });
          Ranked.setRanksByScore(sdocs);

          for (ScoredDocument sdoc : sdocs) {
            trecrun.println(sdoc.toTRECformat(qid, "schuhmacher"));
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

  }
}

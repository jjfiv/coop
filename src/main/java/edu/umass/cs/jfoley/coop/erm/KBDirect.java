package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import edu.umass.cs.jfoley.coop.entityco.ConvertEntityJudgmentData;
import edu.umass.cs.jfoley.coop.entityco.EntityJudgedQuery;
import edu.umass.cs.jfoley.coop.entityco.PMIRankingExperiment;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.lists.Ranked;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author jfoley
 */
public class KBDirect {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    String dataset = argp.get("dataset", "robust04");

    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));
    HashSet<String> validKB = new HashSet<>(LinesIterable.fromFile(argp.get("validKBNames", "validKB.names.gz")).slurp());

    TagTokenizer tok = new TagTokenizer();

    // for mention->entity probs:
    boolean fullWikiKB = argp.get("fullWikiKB", true);
    LocalRetrieval kb = (fullWikiKB) ?
        PMIRankingExperiment.openJeffWiki(argp) :
        new LocalRetrieval("/mnt/scratch3/jfoley/dbpedia.galago");
    try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", (fullWikiKB ? "wiki" : "abstract") +"."+dataset + ".trecrun"))) {
      for (EntityJudgedQuery query : queries) {
        String qid = query.qid;

        // generate query:
        List<String> tokens = tok.tokenize(query.getText()).terms;
        Node sdm = new Node("sdm");
        sdm.addTerms(tokens);

        System.out.println(qid + " " + tokens);

        Parameters qp = argp.clone();
        qp.put("requested", 5000);
        Results res = kb.transformAndExecuteQuery(sdm, qp); // mu, uniw, odw, uww

        List<ScoredDocument> topValid = new ArrayList<>();
        for (ScoredDocument sdoc : res.scoredDocuments) {
          if(validKB.size() == 1000) break;
          if(validKB.contains(sdoc.documentName)) {
            topValid.add(sdoc);
          }
        }

        Ranked.setRanksByScore(topValid);
        for (ScoredDocument scoredDocument : topValid) {
          trecrun.println(scoredDocument.toTRECformat(qid, "direct"));
        }
      }
    }
  }

}

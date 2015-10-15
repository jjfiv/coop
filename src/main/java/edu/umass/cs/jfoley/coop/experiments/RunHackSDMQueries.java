package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.entityco.EntityJudgedQuery;
import org.lemurproject.galago.core.index.mem.MemoryIndex;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley
 */
public class RunHackSDMQueries {
  public static void main(String[] args) throws Exception {
    Parameters argp = Parameters.parseArgs(args);

    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("index", "robust.ints")));
    List<Parameters> queries = argp.getList("queries", Parameters.class);
    String output = argp.get("output", "test.trecrun");
    int requested = argp.get("requested", 1000);

    try (PrintWriter pw = IO.openPrintWriter(output)) {
      for (Parameters jquery : queries) {
        EntityJudgedQuery query = EntityJudgedQuery.fromJSON(jquery);

        System.err.println(query.qid + " " + query.getText());
        List<ScoredDocument> topQL = target.searchQL(query.getText(), "dirichlet", requested*2);

        MemoryIndex memIndex = new MemoryIndex(Parameters.parseArray("nonstemming", false));

        System.err.println("build memoryIndex start");
        for (ScoredDocument scoredDocument : topQL) {
          int docId = (int) scoredDocument.document;
          Document doc = new Document();
          doc.name = scoredDocument.documentName;
          doc.terms = target.translateToTerms(new IntList(target.getCorpus().getDocument(docId)));
          doc.tags = Collections.emptyList();
          memIndex.process(doc);
        }
        System.err.println("build memoryIndex end");

        LocalRetrieval ret = new LocalRetrieval(memIndex);
        Parameters qp = Parameters.create();
        qp.put("requested", 1000);

        Node sdm = new Node("sdm");
        TagTokenizer tok = new TagTokenizer();
        for (String term : tok.tokenize(query.getText()).terms) {
          sdm.addChild(Node.Text(term));
        }

        Results sdmRes = ret.transformAndExecuteQuery(sdm, qp);

        for (ScoredDocument scoredDocument : sdmRes.scoredDocuments) {
          pw.println(scoredDocument.toTRECformat(query.qid, "sdm-hacked"));
        }
      }
    }

  }
}

package edu.umass.cs.jfoley.coop.experiments.rels;

import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.web.WebServer;
import ciir.jfoley.chai.web.json.JSONAPI;
import ciir.jfoley.chai.web.json.JSONMethod;
import org.lemurproject.galago.core.eval.EvalDoc;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class SnippetServer {
  public static class QuerySnippet {
    private final String qid;
    private final String docId;
    private final int beginToken;
    private final int endToken;
    private final String rawText;

    public QuerySnippet(String qid, String docId, int beginToken, int endToken, String rawText) {
      this.qid = qid;
      this.docId = docId;
      this.beginToken = beginToken;
      this.endToken = endToken;
      this.rawText = rawText;
    }

    public Parameters toJSON() {
      return Parameters.parseArray("qid", qid, "docId", docId, "span", Arrays.asList(beginToken, endToken), "text", rawText);
    }
  }
  public static void main(String[] args) throws IOException {
    String dataset = "robust";
    // load document-scores:
    QuerySetResults qresults = new QuerySetResults("/mnt/scratch3/jfoley/snippets/"+dataset+".sdm.trecrun");
    Map<String, String> queries = ScoreDocumentsForSnippets.loadQueries(dataset);

    Map<String, List<QuerySnippet>> snippetsByQuery = new HashMap<>();
    Map<String, Set<String>> docsByQuery = new HashMap<>();

    try (LinesIterable snippetLines = LinesIterable.fromFile("/mnt/scratch3/jfoley/snippets/" + dataset + ".rawsnippets.tsv.gz")) {
      for (String snippetLine : snippetLines) {
        String[] cols = snippetLine.split("\t");
        String qid = cols[0];
        String docId = cols[1];
        int beginToken = Integer.parseInt(StrUtil.takeBefore(cols[2], ","));
        int endToken = Integer.parseInt(StrUtil.takeAfter(cols[2], ","));
        String rawText = cols[4];

        MapFns.extendSetInMap(docsByQuery, qid, docId);
        MapFns.extendListInMap(snippetsByQuery, qid, new QuerySnippet(qid, docId, beginToken, endToken, rawText));
      }
    }

    HashMap<String, JSONMethod> methods = new HashMap<>();

    methods.put("/queries", (p) -> {
      List<Parameters> qp = new ArrayList<>();
      for (Map.Entry<String, String> kv : queries.entrySet()) {
        String qid = kv.getKey();
        qp.add(Parameters.parseArray(
            "qid", qid,
            "text", kv.getValue(),
            "numSnippets", snippetsByQuery.get(qid).size(),
            "numDocuments", docsByQuery.get(qid).size(),
            "numScoredDocs", qresults.get(qid).size()));
      }
      return Parameters.parseArray("queries", qp);
    });

    methods.put("/docScore", (p) -> {
      String qid = p.getAsString("qid");
      String docId = p.getAsString("docId");

      Parameters outp = Parameters.create();
      EvalDoc doc = qresults.get(qid).find(docId);
      if(doc != null) {
        outp.put("score", doc.getScore());
      }
      return outp;
    });

    methods.put("/snippets", (p) -> {
      String qid = p.getAsString("qid");
      int offset = p.get("offset", 0);
      int pageSize = p.get("size", 30);
      return Parameters.parseArray("snippets", ListFns.map(ListFns.slice(snippetsByQuery.get(qid), offset, offset+pageSize), QuerySnippet::toJSON));
    });

    WebServer start = JSONAPI.start(1234, methods);
    start.join();
  }
}

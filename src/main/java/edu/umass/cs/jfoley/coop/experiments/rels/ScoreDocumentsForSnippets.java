package edu.umass.cs.jfoley.coop.experiments.rels;

import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class ScoreDocumentsForSnippets {
  public static Map<String,String> loadQueries(String dataset) throws IOException {
    Map<String, String> queries = new TreeMap<>();
    switch (dataset.toLowerCase()) {
      case "robust":
        for (String tsvLine : LinesIterable.fromFile("/home/jfoley/code/queries/robust04/rob04.titles.tsv").slurp()) {
          String col[] = tsvLine.trim().split("\t");
          queries.put(col[0], col[1]);
        }
        break;
      case "clue12a.sdm":
        for (String tsvLine : LinesIterable.fromFile("/home/jfoley/code/queries/clue12/web1314.queries.tsv").slurp()) {
          String col[] = tsvLine.trim().split("\t");
          queries.put(col[0], col[1]);
        }
        break;
      default: throw new IllegalArgumentException("No such dataset for loadQueries: "+dataset);
    }
    return queries;
  }

  public static void main(String[] args) throws IOException {
    //String dataset = "clue12a.sdm";
    String dataset = "robust";
    //String dataset = "clue12a.sdm";
    LocalRetrieval ret = new LocalRetrieval("/mnt/scratch3/jfoley/"+dataset+".galago");

    Map<String, String> queries = loadQueries(dataset);

    Map<String, Set<String>> workingSetByQuery = new TreeMap<>();

    try (LinesIterable snippetLines = LinesIterable.fromFile("/mnt/scratch3/jfoley/snippets/" + dataset + ".rawsnippets.tsv.gz")) {
      for (String snippetLine : snippetLines) {
        String[] cols = snippetLine.split("\t");
        String qid = cols[0];
        String docId = cols[1];
        MapFns.extendSetInMap(workingSetByQuery, qid, docId);
      }
    }

    Debouncer msg = new Debouncer();
    int i=0;
    try (PrintWriter pw = IO.openPrintWriter("/mnt/scratch3/jfoley/snippets/"+dataset+".sdm.trecrun")) {
      for (Map.Entry<String, Set<String>> kv : workingSetByQuery.entrySet()) {
        String qid = kv.getKey();
        ArrayList<String> ws = new ArrayList<>(kv.getValue());

        Node sdm = new Node("sdm");
        TagTokenizer tok = new TagTokenizer();
        for (String term : tok.tokenize(queries.get(qid)).terms) {
          sdm.add(Node.Text(term));
        }
        Parameters qp = Parameters.create();
        qp.put("working", ws);
        qp.put("requested", ws.size());

        System.err.println("# "+qid+" " +msg.estimate(i++, workingSetByQuery.size())+" ND:"+ws.size());
        for (ScoredDocument scoredDocument : ret.transformAndExecuteQuery(sdm, qp).scoredDocuments) {
          pw.println(scoredDocument.toTRECformat(qid, "galago-sdm"));
        }
      }
    }


  }
}

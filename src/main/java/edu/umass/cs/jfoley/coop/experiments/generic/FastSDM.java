package edu.umass.cs.jfoley.coop.experiments.generic;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.util.IterUtils;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author jfoley
 */
public class FastSDM {
  public static class Effectiveness {
    public static void main(String[] args) throws Exception {
      Parameters retP = Parameters.create();
      IterUtils.addToParameters(retP, SDM2.class);
      LocalRetrieval target = new LocalRetrieval("/mnt/scratch3/jfoley/robust.galago", retP);
      Map<String, String> queries = new TreeMap<>();
      for (String line : LinesIterable.fromFile("/home/jfoley/code/queries/robust04/rob04.titles.tsv").slurp()) {
        String[] row = line.split("\t");
        queries.put(row[0].trim(), row[1].trim());
      }

      TagTokenizer tok = new TagTokenizer();
      try (PrintWriter trecrun = IO.openPrintWriter("latest.trecrun")) {
        for (Map.Entry<String, String> kv : queries.entrySet()) {
          String qid = kv.getKey();
          String text = kv.getValue();

          List<String> terms = tok.tokenize(text).terms;
          Node sdm = new Node("sdm2");
          sdm.addTerms(terms);

          long startTime = System.currentTimeMillis();
          Parameters qp = Parameters.create();
          qp.set("fast", true);
          qp.set("stopUnigrams", true);
          qp.set("stopUnordered", true);
          Results res = target.transformAndExecuteQuery(sdm, qp);
          long endTime = System.currentTimeMillis();

          System.err.println(qid + "\t" + (endTime - startTime));
          res.printToTrecrun(trecrun, qid, "sdm-fast");
        }
      }
    }
  }
  public static class Efficiency {
    public static void main(String[] args) throws Exception {
      String op = "sdm";
      String queryset = "mq";
      int queryIter = 2;

      StringPooler.disable();
      Parameters retP = Parameters.create();
      //retP.put("nodeStatisticsCacheSize", 0L);
      retP.put("namesCacheSize", 100_000);
      IterUtils.addToParameters(retP, SDM2.class);
      LocalRetrieval target = new LocalRetrieval("/mnt/scratch3/jfoley/robust.galago", retP);

      String queryFile;
      switch (queryset) {
        case "mq":
          queryFile = "/home/jfoley/code/queries/million_query_track/mq.20001-60000.tsv";
          break;
        case "robust":
          queryFile = "/home/jfoley/code/queries/robust04/rob04.titles.tsv";
          break;
        default: throw new IllegalArgumentException("queryset");
      }

      Map<String, String> queries = new TreeMap<>();
      for (String line : LinesIterable.fromFile(queryFile).slurp()) {
        String[] row = line.split("\t");
        queries.put(row[0].trim(), row[1].trim());
      }

      Debouncer msg = new Debouncer();
      TagTokenizer tok = new TagTokenizer();
      try(PrintWriter timeWriter = IO.openPrintWriter(queryset+"."+op+".runtimes.tsv")) {
        for (int i = 0; i < queryIter; i++) {
          final int iterationNumber = i;
          queries.entrySet().parallelStream().map(kv -> {
            String qid = kv.getKey();
            String text = kv.getValue();
            if(msg.ready()) {
              System.err.println(iterationNumber+"."+qid);
            }

            List<String> terms = tok.tokenize(text).terms;
            Node sdm = new Node(op);
            sdm.addTerms(terms);

            long startTime = System.currentTimeMillis();
            Parameters qp = Parameters.create();
            qp.set("fast", true);
            qp.set("stopUnigrams", true);
            qp.set("stopUnordered", true);
            Results res = target.transformAndExecuteQuery(sdm, qp);
            long endTime = System.currentTimeMillis();

            return Pair.of(qid, (endTime - startTime));
          }).sequential().sorted(Pair.cmpLeft()).forEach(pr -> {
            timeWriter.println(pr.left + "\t" + pr.right);
          });
        }
      }
      // done
    }
  }
}

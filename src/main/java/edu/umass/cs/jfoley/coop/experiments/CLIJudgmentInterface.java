package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.entityco.ConvertEntityJudgmentData;
import edu.umass.cs.jfoley.coop.entityco.EntityJudgedQuery;
import org.lemurproject.galago.core.eval.EvalDoc;
import org.lemurproject.galago.core.eval.QueryResults;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class CLIJudgmentInterface {

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.create();

    Map<String, Map<String, Double>> judgments = new HashMap<>();
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "dbpedia.ints")));

    String dataset = "robust04";
    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));
    int rankCutoff = argp.get("depth", 5);

    try (LinesIterable lines = LinesIterable.fromFile(argp.get("load", "coop/ecir2016runs/qrels/robust04.x.ent.qrel"))) {
      for (String line : lines) {
        String[] row = line.split("\\s+");
        String qid = row[0];
        String ent = row[2];
        double judgment = Double.parseDouble(row[3]);
        judgments.computeIfAbsent(qid, (ignored) -> new HashMap<>()).put(ent, judgment);
      }
    }

    List<String> runs = new ArrayList<>();
    runs.add("coop/ecir2016runs/robust04runs/robust04.wikisdm.raw.trecrun");
    runs.add("robust04.dbpedia.pmi.m2.p100.trecrun");
    runs.add("robust04.dbpedia.wiki-pmi.m2.p250.trecrun");
    runs.add("robust04.top20.logpmi.m2.trecrun");
    runs.addAll(argp.getAsList("runs", String.class));

    Map<String, HashMap<String, Integer>> topRetrieved = new HashMap<>();
    for (String run : runs) {
      QuerySetResults runData = new QuerySetResults(run);

      for (String qid : runData.getQueryIterator()) {
        HashMap<String, Integer> forThisQuery = topRetrieved.computeIfAbsent(qid, ignored -> new HashMap<>());
        QueryResults results = runData.get(qid);
        for (EvalDoc evalDoc : results.getIterator()) {
          if(evalDoc.getRank() > rankCutoff) break;
          MapFns.addOrIncrement(forThisQuery, evalDoc.getName(), 1);
        }
      }
    }

    Map<String, Map<String, Double>> scored = new HashMap<>();

    for (EntityJudgedQuery query : queries) {
      String qid = query.qid;
      String text = query.getText();
      Map<String,Double> judgedForQuery = judgments.get(qid);
      List<ScoredDocument> topForThis = new ArrayList<>();

      for (Map.Entry<String, Integer> freqEnt : topRetrieved.get(qid).entrySet()) {
        String ent = freqEnt.getKey();
        int count = freqEnt.getValue();
        if(judgedForQuery.containsKey(ent)) {
          continue;
        }
        topForThis.add(new ScoredDocument(ent, -1, count));
      }

      Map<String, Double> output = scored.computeIfAbsent(qid, (ignored) -> new HashMap<>());
      output.putAll(judgedForQuery);

      Collections.sort(topForThis);
      //System.out.println(qid+" "+text);
      for (ScoredDocument entToJudge : topForThis) {
        String ent = entToJudge.documentName;
        Integer entId = dbpedia.getNames().getReverse(ent);

        //System.out.println("\t"+entToJudge.documentName+"\t"+entToJudge.score);
        System.out.println(qid+" "+text);
        System.out.println("\thttp://dbpedia.org/resource/"+ent);
        if(entId != null) {
          List<String> doc = dbpedia.translateToTerms(new IntList(dbpedia.getCorpus().getDocument(entId)));
          for (int i = 0; i < doc.size(); i+=10) {
            System.out.println("\t"+ StrUtil.join(ListFns.slice(doc, i, i + 10)));
          }
        }
        System.out.println("0. Non-Relevant, 1. Remotely Relevant, 2. Relevant, 3. Very Relevant, 4. Highly Relevant\n");

        while(true) {
          String input = readString("judgment> ").trim();
          if(input.isEmpty()) break;
          try {
            double score = Double.parseDouble(input);
            output.put(ent, score);
            break;
          } catch (Exception e) {
            e.printStackTrace(System.err);
          }
        }

        save(scored);
      }
    }


  }

  private static void save(Map<String, Map<String, Double>> output) throws IOException {
    try (PrintWriter out = IO.openPrintWriter("output.ent.qrel")) {
      for (Map.Entry<String, Map<String, Double>> kv : output.entrySet()) {
        String qid = kv.getKey();
        for (Map.Entry<String, Double> dj : kv.getValue().entrySet()) {
          String doc = dj.getKey();
          double score = dj.getValue();
          out.println(qid+" Q0 "+doc+" "+score);
        }
      }
    }
  }

  public static String readString(String prompt) throws IOException {
    System.out.print(prompt);
    StringBuilder sb = new StringBuilder();
    while(true) {
      int ch = System.in.read();
      if(ch == -1) break;
      if(ch == '\n' || ch == '\r') break;
      sb.append((char) ch);
    }
    return sb.toString();
  }
}

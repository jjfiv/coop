package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author jfoley
 */
public class ConvertEntityJudgmentData {

  //
  // Generate unfiltered Wiki-SDM baseline:
  // sh ~/code/stable/galago-3-8/core/target/appassembler/bin/galago batch-search ../../../coop/data/robust04.json --index= /mnt/scratch/jfoley/jeff-wiki.galago/full-wiki-stanf3_context_g351/ --stemming=false --mu=96400.0 --defaultSmoothingMu=96400.0 --uniw=0.29 --odw=0.21 --uww=0.5 > jdalton.wikisdm.robust04.trecrun
  //

  public static List<EntityJudgedQuery> parseQueries(File input) {
    try {
      return ListFns.map(Parameters.parseFile(input).getAsList("queries", Parameters.class), EntityJudgedQuery::fromJSON);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) throws IOException {
    Map<String, Map<String, Double>> judgedEntitiesByQuery = loadEntityJudgments();
    Map<String, String> queries = loadQueriesFromMentionsFile(judgedEntitiesByQuery);

    ArrayList<Parameters> data = new ArrayList<>();
    try (PrintWriter qrel = IO.openPrintWriter("coop/data/robust04.ent.qrel")) {
      try (PrintWriter tsv = IO.openPrintWriter("coop/data/robust04.tsv")) {
        for (String qid : queries.keySet()) {
          EntityJudgedQuery ejq = new EntityJudgedQuery(qid, queries.get(qid));
          ejq.judgments.putAll(judgedEntitiesByQuery.get(qid));

          List<Map.Entry<String, Double>> collect = ejq.judgments.entrySet().stream().sorted((lhs, rhs) -> -Double.compare(lhs.getValue(), rhs.getValue())).limit(5).collect(Collectors.toList());
          System.err.println(ejq.text + "\t" + collect);
          data.add(ejq.toJSON());

          // print qrel
          for (Map.Entry<String, Double> kv : ejq.judgments.entrySet()) {
            qrel.println(ejq.qid + " Q0 " + kv.getKey() + " " + (int) Math.round(kv.getValue()));
          }
          tsv.println(qid+"\t"+ejq.text);
        }
      }
    }

    Parameters output = Parameters.create();
    output.put("queries", data);

    try (PrintWriter out = IO.openPrintWriter("coop/data/robust04.json")) {
      out.println(output.toPrettyString());
    }


  }

  private static Map<String, String> loadQueriesFromMentionsFile(Map<String, Map<String, Double>> judgedEntitiesByQuery) throws IOException {
    Map<String,String> queries = new HashMap<>();
    List<String> lines = LinesIterable.fromFile("rob_document_mentions.data").slurp();
    for (String line : lines) {
      if(line.startsWith("rob04_qid")) continue;
      String[] data = line.split("\t");
      String qid = data[0];
      String query = data[1];
      if(!judgedEntitiesByQuery.containsKey(qid)) continue;
      queries.put(qid, query);
    }
    return queries;
  }

  private static Map<String, Map<String, Double>> loadEntityJudgments() throws IOException {
    Map<String, Map<String, Double>> judgedEntitiesByQuery = new HashMap<>();

    List<String> lines = LinesIterable.fromFile("rob_entity_ranking.qrel").slurp();
    for (String line : lines) {
      String[] cols = line.split("\\s+");
      String qid = cols[0];
      String ent = cols[2];
      double score = Double.parseDouble(cols[3])-1; // 1->0, 5->4 ...
      judgedEntitiesByQuery.computeIfAbsent(qid, (ignored) -> new HashMap<>()).put(ent, score);
    }
    return judgedEntitiesByQuery;
  }
}

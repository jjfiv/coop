package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import org.lemurproject.galago.utility.Parameters;

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
public class ConvertClueEntityData {

  public static void main(String[] args) throws IOException {
    Map<String, Map<String, Double>> judgedEntitiesByQuery = loadEntityJudgments();
    Map<String, String> queries = loadQueriesFromMentionsFile(judgedEntitiesByQuery);

    ArrayList<Parameters> data = new ArrayList<>();
    try (PrintWriter out = IO.openPrintWriter("coop/data/clue12.tsv")) {
      for (String qid : queries.keySet()) {
        EntityJudgedQuery ejq = new EntityJudgedQuery(qid, queries.get(qid));
        ejq.judgments.putAll(judgedEntitiesByQuery.get(qid));

        List<Map.Entry<String, Double>> collect = ejq.judgments.entrySet().stream().sorted((lhs, rhs) -> -Double.compare(lhs.getValue(), rhs.getValue())).limit(5).collect(Collectors.toList());
        System.err.println(ejq.text + "\t" + collect);
        data.add(ejq.toJSON());

        out.println(qid+"\t"+ejq.text);
      }
    }

    Parameters output = Parameters.create();
    output.put("data", data);

    try (PrintWriter out = IO.openPrintWriter("coop/data/clue12.json")) {
      out.println(output.toPrettyString());
    }


  }

  private static Map<String, String> loadQueriesFromMentionsFile(Map<String, Map<String, Double>> judgedEntitiesByQuery) throws IOException {
    Map<String,String> queries = new HashMap<>();
    List<String> lines = LinesIterable.fromFile("clueweb_sdm_top20docs.data").slurp();
    for (String line : lines) {
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

    List<String> lines = LinesIterable.fromFile("clueweb_entity_ranking.qrel").slurp();
    for (String line : lines) {
      String[] cols = line.split("\\s+");
      String qid = cols[0];
      String ent = cols[2];
      int score = Integer.parseInt(cols[3]);
      judgedEntitiesByQuery.computeIfAbsent(qid, (ignored) -> new HashMap<>()).put(ent, (double) score);
    }
    return judgedEntitiesByQuery;
  }
}

package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.lemurproject.galago.core.eval.QueryJudgments;
import org.lemurproject.galago.core.eval.QuerySetJudgments;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class TrecRunReranker {

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    String outputFileName = argp.getString("output");
    Map<String, Map<String, Map<String, Double>>> queryToDocumentToFeatures = new HashMap<>();
    Map<String, QueryJudgments> judgments = QuerySetJudgments.loadJudgments(argp.getString("judgments"), true, true);

    Set<String> featureNames = new HashSet<>();
    for (String inputName : argp.getAsList("input", String.class)) {
      // load each trecrun file:
      for (String line : LinesIterable.fromFile(inputName).slurp()) {
        String[] cols = line.split("\\s+");
        String qid = cols[0];
        String doc = cols[2];
        int rank = Integer.parseInt(cols[3]);
        double score = Double.parseDouble(cols[4]);

        Map<String, Map<String, Double>> queryInfo = queryToDocumentToFeatures.get(qid);
        if(queryInfo == null) {
          queryInfo = new HashMap<>();
          queryToDocumentToFeatures.put(qid, queryInfo);
        }
        Map<String,Double> docFeatures = queryInfo.get(doc);
        if(docFeatures == null) {
          docFeatures = new HashMap<>();
          queryInfo.put(doc, docFeatures);
        }
        docFeatures.put(inputName, score);
        featureNames.add(inputName);
      }
    }

    System.err.println("Loaded "+featureNames+" as trecrun features...");

    if(argp.containsKey("pagerank")) {
      featureNames.add("pagerank");
      HashSet<String> allDocuments = new HashSet<>();
      for (Map<String, Map<String, Double>> docToFeatures : queryToDocumentToFeatures.values()) {
        allDocuments.addAll(docToFeatures.keySet());
      }

      // load necessary pageranks from file:
      TObjectDoubleHashMap<String> pageRanks = new TObjectDoubleHashMap<>();
      try (LinesIterable lines = LinesIterable.fromFile(argp.getString("pagerank"))) {
        for (String line : lines) {
          String[] data = line.split("\t");
          String id = data[0];
          if(!allDocuments.contains(id)) {
            continue;
          }
          double score = Double.parseDouble(data[1]);
          pageRanks.put(id, score);

          // leave loop as soon as we're done:
          if(pageRanks.size() >= allDocuments.size()) break;
        }
      }

      // weave in
      for (Map<String, Map<String, Double>> docToFeatures : queryToDocumentToFeatures.values()) {
        for (Map.Entry<String, Map<String, Double>> dfv : docToFeatures.entrySet()) {
          String doc = dfv.getKey();
          Map<String, Double> features = dfv.getValue();
          features.put("pagerank", pageRanks.get(doc));
        }
      }
    }

    // zscore normalization...


    List<String> featureNumTable = new ArrayList<>(featureNames);

    try (PrintWriter out = IO.openPrintWriter(outputFileName)) {
      for (Map.Entry<String, Map<String, Map<String, Double>>> qidToRest : queryToDocumentToFeatures.entrySet()) {
        String qid = qidToRest.getKey();
        QueryJudgments queryJudgments = judgments.get(qid);

        for (Map.Entry<String, Map<String, Double>> docToFeatures : qidToRest.getValue().entrySet()) {
          String doc = docToFeatures.getKey();

          int rel = 0;
          if (queryJudgments != null) {
            rel = queryJudgments.get(doc);
          }

          StringBuilder featureBuilder = new StringBuilder();
          TDoubleArrayList scores = new TDoubleArrayList();
          for (Map.Entry<String, Double> kv : docToFeatures.getValue().entrySet()) {
            String feature = kv.getKey();
            int featureNum = featureNumTable.indexOf(feature);
            double value = kv.getValue();
            scores.add(value);
            featureBuilder.append(' ').append(featureNum).append(":").append(value);
          }

          int MAX_FEATURE = featureNumTable.size();
          int MIN_FEATURE = MAX_FEATURE + 1;
          int SUM_FEATURE = MIN_FEATURE + 1;
          int AVG_FEATURE = SUM_FEATURE + 1;
          featureBuilder.append(' ').append(MAX_FEATURE).append(":").append(scores.max());
          featureBuilder.append(' ').append(MIN_FEATURE).append(":").append(scores.min());
          featureBuilder.append(' ').append(SUM_FEATURE).append(":").append(scores.sum());
          featureBuilder.append(' ').append(AVG_FEATURE).append(":").append(scores.sum() / ((double) scores.size()));

          out.printf("%d qid:%s %s # %s\n", rel, qid, StrUtil.compactSpaces(featureBuilder.toString()), doc);
        }
      }
    }


  }
}

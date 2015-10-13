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

    final boolean appendSummaryFeatures = argp.get("summaryFeatures", true);

    Set<String> featureNames = new HashSet<>();
    for (String inputName : argp.getAsList("input", String.class)) {
      // load each trecrun file:
      for (String line : LinesIterable.fromFile(inputName).slurp()) {
        String[] cols = line.split("\\s+");
        String qid = cols[0];
        String doc = cols[2];
        int rank = Integer.parseInt(cols[3]);
        double score = Double.parseDouble(cols[4]);

        if(!judgments.containsKey(qid)) {
          continue;
        }

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

    System.err.println("Loaded " + featureNames + " as trecrun features...");

    List<String> staticPriors = argp.getAsList("static", String.class);
    if(argp.isString("pagerank")) {
      staticPriors.add(argp.getString("pagerank"));
    }

    if(!staticPriors.isEmpty()) {
      featureNames.addAll(staticPriors);

      HashSet<String> allDocuments = new HashSet<>();
      for (Map<String, Map<String, Double>> docToFeatures : queryToDocumentToFeatures.values()) {
        allDocuments.addAll(docToFeatures.keySet());
      }

      Map<String, TObjectDoubleHashMap<String>> priors = new HashMap<>();

      for (String staticPrior : staticPriors) {
        // load necessary priors from file:
        TObjectDoubleHashMap<String> pageRanks = new TObjectDoubleHashMap<>();
        priors.put(staticPrior, pageRanks);

        try (LinesIterable lines = LinesIterable.fromFile(staticPrior)) {
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

      }

      System.err.println("Loaded " + staticPriors + " as static priors...");

      // weave in
      for (Map<String, Map<String, Double>> docToFeatures : queryToDocumentToFeatures.values()) {
        for (Map.Entry<String, Map<String, Double>> dfv : docToFeatures.entrySet()) {
          String doc = dfv.getKey();
          Map<String, Double> features = dfv.getValue();
          for (Map.Entry<String, TObjectDoubleHashMap<String>> kv : priors.entrySet()) {
            String fname = kv.getKey();
            TObjectDoubleHashMap<String> docPriors = kv.getValue();
            if(docPriors.containsKey(doc)) {
              double value = docPriors.get(doc);
              features.put(fname, value);
            }
          }
        }
      }
    }

    // calculate minimums:
    for (Map.Entry<String, Map<String, Map<String, Double>>> stringMapEntry : queryToDocumentToFeatures.entrySet()) {
      String qid = stringMapEntry.getKey();
      System.err.println(qid);
      Map<String, Map<String, Double>> byQuery = stringMapEntry.getValue();

      // for each query:
      Map<String, Double> minFeatures = new HashMap<>();
      for (Map<String, Double> features : byQuery.values()) {
        for (Map.Entry<String, Double> feature : features.entrySet()) {
          String fname = feature.getKey();
          double value = feature.getValue();

          Double score = minFeatures.get(fname);
          if(score == null || score > value) {
            minFeatures.put(fname, value);
          }
        }
      }

      byQuery.computeIfAbsent("__MIN__", ignored -> new HashMap<>()).putAll(minFeatures);
    }


    // zscore normalization...


    List<String> featureNumTable = new ArrayList<>(featureNames);

    try (PrintWriter out = IO.openPrintWriter(outputFileName)) {
      for (Map.Entry<String, Map<String, Map<String, Double>>> qidToRest : queryToDocumentToFeatures.entrySet()) {
        String qid = qidToRest.getKey();
        Map<String, Map<String, Double>> docToFeaturesMap = qidToRest.getValue();
        QueryJudgments queryJudgments = judgments.get(qid);

        for (Map.Entry<String, Map<String, Double>> docToFeatures : docToFeaturesMap.entrySet()) {
          String doc = docToFeatures.getKey();
          Map<String, Double> docFeatures = docToFeatures.getValue();

          int rel = 0;
          if (queryJudgments != null) {
            rel = queryJudgments.get(doc);
          }

          StringBuilder featureBuilder = new StringBuilder();
          TDoubleArrayList scores = new TDoubleArrayList();


          for (String feature : featureNames) {
            int featureNum = featureNumTable.indexOf(feature);
            try {
              double value = docFeatures.getOrDefault(feature,
                  Objects.requireNonNull(
                      Objects.requireNonNull(docToFeaturesMap.get("__MIN__"))
                          .get(feature)));
              scores.add(value);
              featureBuilder.append(' ').append(featureNum+1).append(":").append(value);
            } catch (NullPointerException npe) {
              System.err.println(qid+" "+feature);
              System.err.println(qid+" "+docToFeaturesMap.get("__MIN__"));
              throw npe;
            }
          }

          if(appendSummaryFeatures) {
            int MAX_FEATURE = featureNumTable.size();
            int MIN_FEATURE = MAX_FEATURE + 1;
            int SUM_FEATURE = MIN_FEATURE + 1;
            int AVG_FEATURE = SUM_FEATURE + 1;
            featureBuilder.append(' ').append(MAX_FEATURE+1).append(":").append(scores.max());
            featureBuilder.append(' ').append(MIN_FEATURE+1).append(":").append(scores.min());
            featureBuilder.append(' ').append(SUM_FEATURE+1).append(":").append(scores.sum());
            featureBuilder.append(' ').append(AVG_FEATURE+1).append(":").append(scores.sum() / ((double) scores.size()));
          }

          out.printf("%d qid:%s %s # %s\n", rel, qid, StrUtil.compactSpaces(featureBuilder.toString()), doc);
        }
      }
    }

    System.out.println(outputFileName);

  }
}

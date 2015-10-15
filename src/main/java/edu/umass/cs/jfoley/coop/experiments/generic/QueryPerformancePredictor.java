package edu.umass.cs.jfoley.coop.experiments.generic;

import ciir.jfoley.chai.collections.util.SetFns;
import ciir.jfoley.chai.io.IO;
import org.lemurproject.galago.core.eval.EvalDoc;
import org.lemurproject.galago.core.eval.QueryResults;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Build a simple feature based on overlap of a trusted ranking with a untrusted.
 * @author jfoley
 */
public class QueryPerformancePredictor {
  public static Set<String> topK(int k, String qid, QuerySetResults res) {
    Set<String> topItems = new HashSet<>();
    QueryResults queryResults = res.get(qid);
    if(queryResults == null) return topItems;
    for (EvalDoc evalDoc : queryResults.getIterator()) {
      if(evalDoc.getRank() <= k) {
        topItems.add(evalDoc.getName());
      }
    }
    return topItems;
  }
  public static Set<String> all(String qid, QuerySetResults res) {
    Set<String> topItems = new HashSet<>();
    QueryResults queryResults = res.get(qid);
    if(queryResults == null) return topItems;
    for (EvalDoc evalDoc : queryResults.getIterator()) {
      topItems.add(evalDoc.getName());
    }
    return topItems;
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    QuerySetResults trusted = new QuerySetResults(argp.getString("trusted"));
    QuerySetResults sketchy = new QuerySetResults(argp.getString("sketchy"));
    int depth = argp.get("depth", 10);
    String output = argp.getString("output");

    try (PrintWriter trecrun = IO.openPrintWriter(output)) {
      for (String qid : trusted.getQueryIterator()) {
        Set<String> topTrusted = topK(depth, qid, trusted);
        Set<String> topSketchy = topK(depth, qid, sketchy);

        double confidence = SetFns.intersection(topSketchy, topTrusted).size() / (double) depth;

        Set<String> totalItems = new HashSet<>();
        totalItems.addAll(all(qid, trusted));
        totalItems.addAll(all(qid, sketchy));

        List<String> confidenceToEveryDoc = new ArrayList<>(totalItems);
        Collections.sort(confidenceToEveryDoc);

        for (int i = 0; i < confidenceToEveryDoc.size(); i++) {
          trecrun.printf("%s Q0 %s %d %1.5f qpp\n", qid, confidenceToEveryDoc.get(i), i + 1, confidence);
        }
      }
    }
  }
}

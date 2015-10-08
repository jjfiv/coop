package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.lists.Ranked;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class PooledFilterRun {
  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);

    // Schuhmacher uses qrel extension for trecruns :(
    Map<String, List<ScoredDocument>> pooled = JeffWikiFilterRun.loadTrecrun(new File(argp.get("rerank", "coop/schuhmacher/models_baselines_rewq/WikiSDM.txt.ranking.qrel")));

    Map<String, Set<String>> valid = MapFns.mapValues(pooled, (sdocs) -> new HashSet<>(ListFns.map(sdocs, ScoredDocument::getName)));

    File inputTrecrun = new File(argp.get("input", "robust04.dbpedia.pmi.m2.p100.trecrun"));
    assert(inputTrecrun.exists());
    Map<String, List<ScoredDocument>> results = new TreeMap<>();
    String system = null;
    try (LinesIterable inputLines = LinesIterable.fromFile(inputTrecrun)) {
      for (String line : inputLines) {
        String[] cols = line.split("\\s+");
        String qid = cols[0];
        String id = cols[2];
        double score = Double.parseDouble(cols[4]);
        if(system == null) {
          system = cols[5] + "-pooled-only";
        }
        MapFns.extendListInMap(results, qid, new ScoredDocument(id, -1, score));
      }
    }

    File outputTrecrun = new File(argp.get("output", "robust04.dbpedia.pmi.m2.p100.pooled.trecrun"));
    try (PrintWriter output = IO.openPrintWriter(outputTrecrun.getAbsolutePath())) {

      for (String qid : valid.keySet()) {
        List<ScoredDocument> rankedList = results.get(qid);
        List<ScoredDocument> validList = new ArrayList<>(rankedList.size());
        Set<String> validNames = valid.get(qid);

        Map<String, Double> rankedByInputMethod = new HashMap<>();
        for (ScoredDocument scoredDocument : rankedList) {
          rankedByInputMethod.put(scoredDocument.documentName, scoredDocument.reciprocalRank());
        }

        for (String validName : validNames) {
          Double score = rankedByInputMethod.getOrDefault(validName, 0.0);
          validList.add(new ScoredDocument(validName, -1, score));
        }

        System.err.println(qid+"\t"+rankedList.size()+"\t"+validList.size());

        Ranked.setRanksByScore(validList);
        Collections.sort(validList, new ScoredDocument.ScoredDocumentComparator());
        for (ScoredDocument scoredDocument : validList) {
          //System.err.println("\t"+scoredDocument.getName());
          output.println(scoredDocument.toTRECformat(qid, system));
        }
      }


    }
  }
}

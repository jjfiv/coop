package edu.umass.cs.jfoley.coop.entityco;

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
public class JeffWikiFilterRun {

  public static String loadTrecrun(File input, Map<String, List<ScoredDocument>> output) throws IOException {
    String system = null;
    try (LinesIterable inputLines = LinesIterable.fromFile(input)) {
      for (String line : inputLines) {
        String[] cols = line.split("\\s+");
        String qid = cols[0];
        String id = cols[2];
        if(system == null) { system = cols[5]; }
        double score = Double.parseDouble(cols[4]);
        MapFns.extendListInMap(output, qid, new ScoredDocument(id, -1, score));
      }
    }
    return system;
  }

  public static Map<String, List<ScoredDocument>> loadTrecrun(File input) throws IOException {
    Map<String, List<ScoredDocument>> output = new TreeMap<>();
    loadTrecrun(input, output);
    return output;
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);

    HashSet<String> validNames = new HashSet<>(LinesIterable.fromFile(argp.get("names", "jeff-wiki.ids.gz")).slurp());
    System.out.println("<"+validNames.iterator().next()+">");


    File inputTrecrun = new File(argp.get("input", "robust04.dbpedia.pmi.m2.p100.trecrun"));
    assert(inputTrecrun.exists());

    Map<String, List<ScoredDocument>> results = new TreeMap<>();
    String system = loadTrecrun(inputTrecrun, results) + "-filtered";

    File outputTrecrun = new File(argp.get("output", "robust04.dbpedia.pmi.m2.p100.filtered.trecrun"));
    try (PrintWriter output = IO.openPrintWriter(outputTrecrun.getAbsolutePath())) {

      for (Map.Entry<String, List<ScoredDocument>> kv : results.entrySet()) {
        String qid = kv.getKey();
        List<ScoredDocument> rankedList = kv.getValue();
        List<ScoredDocument> validList = new ArrayList<>(rankedList.size());

        for (ScoredDocument scoredDocument : rankedList) {
          if(validNames.contains(scoredDocument.documentName)) {
            validList.add(scoredDocument);
          }
          if(validList.size() >= 1000) break;
        }

        System.err.println(qid+"\t"+rankedList.size()+"\t"+validList.size());

        Ranked.setRanksByScore(validList);
        for (ScoredDocument scoredDocument : validList) {
          output.println(scoredDocument.toTRECformat(qid, system));
        }
      }


    }
  }
}

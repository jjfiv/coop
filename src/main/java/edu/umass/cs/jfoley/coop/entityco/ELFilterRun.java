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
public class ELFilterRun {
  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);

    Map<String, Set<String>> entitiesInTopSDMDocsByQuery = new HashMap<>();

    try (LinesIterable lines = LinesIterable.fromFile("rob_document_mentions.data")) {
      List<String> header = Arrays.asList(lines.readLine().split("\t"));
      int entityIndex = header.indexOf("dbpedia_entity");
      int qidIndex = header.indexOf("rob04_qid");
      for (String line : lines) {
        String[] row = line.split("\t");
        String entity = row[entityIndex];
        String qid = row[qidIndex];
        MapFns.extendSetInMap(entitiesInTopSDMDocsByQuery, qid, entity);
      }
    }

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
          system = cols[5] + "-filtered";
        }
        MapFns.extendListInMap(results, qid, new ScoredDocument(id, -1, score));
      }
    }


    File outputTrecrun = new File(argp.get("output", "robust04.dbpedia.pmi.m2.p100.elf.trecrun"));
    try (PrintWriter output = IO.openPrintWriter(outputTrecrun.getAbsolutePath())) {

      for (Map.Entry<String, List<ScoredDocument>> kv : results.entrySet()) {
        String qid = kv.getKey();
        List<ScoredDocument> rankedList = kv.getValue();
        List<ScoredDocument> validList = new ArrayList<>(rankedList.size());
        Set<String> validNames = entitiesInTopSDMDocsByQuery.get(qid);

        for (ScoredDocument scoredDocument : rankedList) {
          if(validNames.contains(scoredDocument.documentName)) {
            validList.add(scoredDocument);
          }
        }

        System.err.println(qid+"\t"+rankedList.size()+"\t"+validList.size());

        Ranked.setRanksByScore(validList);
        for (ScoredDocument scoredDocument : validList) {
          //System.err.println("\t"+scoredDocument.getName());
          output.println(scoredDocument.toTRECformat(qid, system));
        }
      }


    }
  }
}

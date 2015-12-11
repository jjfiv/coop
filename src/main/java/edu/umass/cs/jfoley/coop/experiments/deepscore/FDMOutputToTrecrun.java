package edu.umass.cs.jfoley.coop.experiments.deepscore;

import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.io.archive.ZipArchive;
import ciir.jfoley.chai.io.archive.ZipArchiveEntry;
import ciir.jfoley.chai.string.StrUtil;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.lists.Ranked;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author jfoley
 */
public class FDMOutputToTrecrun {
  public static void main(String[] args) throws IOException {
    TIntObjectHashMap<String> names = new TIntObjectHashMap<>();
    try (LinesIterable lines = LinesIterable.fromFile("/mnt/net/roaming/jfoley/web-docs/robust.ids.tsv")) {
      for (String line : lines) {
        String[] row = line.split("\t");
        int id = Integer.parseInt(row[0]);
        String name = row[1].trim();
        names.put(id, name);
      }
    }
    System.err.println("loaded "+names.size()+" names!");
    try (PrintWriter trecrun = IO.openPrintWriter("latest.trecrun")) {
      try (ZipArchive zip = ZipArchive.open("/mnt/net/roaming/jfoley/web-docs/robust.fdm.fixed.zip")) {
        for (ZipArchiveEntry zipArchiveEntry : zip.listEntries()) {
          String n = zipArchiveEntry.getName();
          String[] parts = StrUtil.takeBetween(n, "robust.", ".tsv").split("\\.");
          String qid = parts[0];
          System.err.println(qid);

          TopKHeap<ScoredDocument> topScored = new TopKHeap<>(1000);
          try (LinesIterable lines = zipArchiveEntry.getLines()) {
            for (String line : lines) {
              String[] dat = line.split("\t");
              int id = Integer.parseInt(dat[0]);
              double score = Double.parseDouble(dat[1]);
              ScoredDocument sdoc = new ScoredDocument(id, score);
              topScored.offer(sdoc);
            }
          }
          List<ScoredDocument> docs = topScored.getSorted();
          Ranked.setRanksByScore(docs);

          for (ScoredDocument doc : docs) {
            doc.documentName = names.get((int) doc.document);
            trecrun.println(doc.toTRECformat(qid));
          }
        }
      }
    }
  }
}

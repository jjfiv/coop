package edu.umass.cs.jfoley.coop.entityco.facc;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

/**
 * @author jfoley
 */
public class FACCFreebaseToDbpedia {
  public static final String sameAsLink = "<http://www.w3.org/2002/07/owl#sameAs>";
  public static void main(String[] args) throws IOException {
    HashMap<String,String> freebaseToDbpedia = new HashMap<>();
    try (LinesIterable lines = LinesIterable.fromFile("/mnt/scratch/jfoley/freebase_links_en.nt.gz")) {
      for (String line : lines) {
        if(line.charAt(0) == '#') continue;
        int pos = line.indexOf(sameAsLink);
        if(pos < 0) continue;
        String lhs = line.substring(0, pos-1);
        String rhs = line.substring(pos+sameAsLink.length()+1);

        String mid = "/m/"+StrUtil.takeBeforeLast(StrUtil.takeAfter(rhs, "<http://rdf.freebase.com/ns/m."), ">");
        String dbpedia = StrUtil.takeBeforeLast(StrUtil.takeAfterLast(lhs, '/'), ">");

        //System.err.println(dbpedia+"\t"+mid);
        freebaseToDbpedia.put(mid, dbpedia);
      }
    }

    System.err.println("Loaded " + freebaseToDbpedia.size() + " Freebase->Dbpedia mappings.");


    int skips = 0;
    try (PrintWriter output = IO.openPrintWriter("facc_09_dbpedia_mentions.tsv.gz")) {
      try (LinesIterable lines = LinesIterable.fromFile("facc_09_mentions.tsv.gz")) {
        for (String line : lines) {
          String[] row = line.split("\t");
          String mid = row[1];
          String dbpid = freebaseToDbpedia.get(mid);
          if(dbpid == null) {
            skips++;
            continue;
          }
          output.println(row[0]+"\t"+dbpid+"\t"+row[2]);
        }
      }
    }

    System.err.println("Translated mentions, skipped="+skips);
  }
}

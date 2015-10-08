package edu.umass.cs.jfoley.coop.pagerank;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author jfoley
 */
public class DBPediaWikiWikiToSRT {
  public static final String LinkRelation = "<http://dbpedia.org/ontology/wikiPageWikiLink>";
  public static void main(String[] args) throws IOException {
    int written = 0;
    try (PrintWriter output = IO.openPrintWriter("/mnt/scratch/jfoley/dbpedia.srt.gz");
         LinesIterable lines = LinesIterable.fromFile( "/mnt/scratch/jfoley/page-links_en.nt.gz")) {
      for (String line : lines) {
        if(line.charAt(0) == '#') continue;
        int pos = line.indexOf(LinkRelation);
        if(pos < 0) continue;

        String lhs = line.substring(0, pos-1).trim();
        String rhs = line.substring(pos+LinkRelation.length()).trim();

        String src = StrUtil.takeBeforeLast(StrUtil.takeAfterLast(lhs, '/'), ">");
        String target = StrUtil.takeBeforeLast(StrUtil.takeAfterLast(rhs, '/'), ">");
        output.println(src+"\t"+target);
        written++;
      }
    }

    System.err.println("Wrote "+written+" edges.");
  }
}

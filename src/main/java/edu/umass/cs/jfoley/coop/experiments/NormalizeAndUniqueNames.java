package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author jfoley.
 */
public class NormalizeAndUniqueNames {
  public static void main(String[] args) throws IOException {
    Debouncer msg = new Debouncer(1000);
    try (PrintWriter output = IO.openPrintWriter("clue.cleaned.names.gz");
        LinesIterable lines = LinesIterable.fromFile("clue.uniq.names.gz")) {
      for (String line : lines) {
        output.println(StrUtil.collapseSpecialMarks(line.trim().toLowerCase()));
        if(msg.ready()) {
          System.out.println("Lines/s: "+msg.estimate(lines.getLineNumber()));
        }
      }
    }


  }
}

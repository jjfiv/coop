package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class SplitRanklib {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    int splitId = argp.getInt("split");
    //String dataset = argp.get("dataset", "clue12");
    String dataset = argp.get("dataset", "robust04");

    List<Tuning.KCVSplit> splits = new ArrayList<>();
    switch (dataset) {
      case "robust04":
        splits.addAll(Tuning.robustSplits);
        break;
      case "clue12":
        splits.addAll(Tuning.clueSplits);
        break;
      default:
        throw new UnsupportedOperationException();
    }

    Tuning.KCVSplit split = splits.get(splitId);

    try (PrintWriter trainOutput = IO.openPrintWriter(argp.getString("train"));
         PrintWriter testOutput = IO.openPrintWriter(argp.getString("test"));
         LinesIterable lines = LinesIterable.fromFile(argp.getString("input"))) {
      for (String line : lines) {
        if(line.trim().isEmpty()) continue;

        String qid = null;
        for (String s : line.split("\\s+")) {
          if(s.startsWith("qid:")) {
            qid = StrUtil.takeAfter(s, "qid:");
            break;
          }
        }

        if(qid == null) throw new RuntimeException(lines.getLineNumber()+": "+line);

        if(split.train.contains(qid)) {
          trainOutput.println(line);
        } else if(split.test.contains(qid)) {
          testOutput.println(line);
        }
      }
    }
  }
}

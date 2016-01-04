package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.umass.edu.learning.DenseDataPoint;
import ciir.umass.edu.utilities.RankLibError;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class ZScoreMergeRanklib {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    Map<String, Map<String, DenseDataPoint>> lhs = new HashMap<>();
    Map<String, Map<String, DenseDataPoint>> rhs = new HashMap<>();

    loadRanklib(lhs, argp.getString("lhs"));
    loadRanklib(rhs, argp.getString("rhs"));


  }

  private static void loadRanklib(Map<String, Map<String, DenseDataPoint>> lhs, String fileName) throws IOException {
    try (LinesIterable lines = LinesIterable.fromFile(fileName)) {
      for (String line : lines) {
        try {
          DenseDataPoint pt = new DenseDataPoint(line);
          String qid = pt.getID();
          String docName = StrUtil.takeAfter(pt.getDescription(), "#").trim();


          lhs.computeIfAbsent(qid, missing -> new HashMap<>())
             .put(docName, pt);
        } catch (RankLibError rle) {
          throw new IOException("Ranklib Error in "+fileName+":"+lines.getLineNumber());
        }
      }
    }

  }
}

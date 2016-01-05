package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import ciir.umass.edu.features.LibSVMFormat;
import ciir.umass.edu.learning.Dataset;
import ciir.umass.edu.learning.DenseDataPoint;
import ciir.umass.edu.learning.PointBuilder;
import ciir.umass.edu.utilities.RankLibError;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * By applying ZScore per-feature, we can deal with the fact some feature-document-query tuples must be unknown -- they will be interpreted as a zero, which is at the mean, or as neutral as possible in a per-feature way.
 * We also experiment with adding a paired feature -- whether the feature exists for each one. Nonlinear models should be able to take advantage of these correlations.
 * @author jfoley
 */
public class ZScoreMergeRanklib {
  public static final class RanklibAtom {
    final String qid;
    final String document;
    final int featureId;
    float featureValue;

    public RanklibAtom(String qid, String document, int featureId, float featureValue) {
      this.qid = qid;
      this.document = document;
      this.featureId = featureId;
      this.featureValue = featureValue;
    }
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    String lhs = argp.get("lhs", "/home/jfoley/code/synthesis/coop/ecir2016runs/rewq/rewq-all.robust.fixed.ranklib");
    String rhs = argp.get("rhs", "/home/jfoley/code/synthesis/coop/ecir2016runs/sigir-dec30/robust04-tuned/ez/highst.cheat.ranklib");

    boolean existsFeatures = argp.get("addExistsFeatures", false); // this does way worse on first pass.

    HashMap<String, Float> labels = new HashMap<>();
    HashMap<Integer, StreamingStats> featureStats = new HashMap<>();
    HashMap<Integer, List<RanklibAtom>> featuresById = new HashMap<>();

    int featureOffset = 0;
    featureOffset = loadRanklib(featureStats, featuresById, labels, lhs, featureOffset);
    System.out.println("FeatureOffset: "+featureOffset);
    System.out.println("FeaturesSoFar: "+featureStats.keySet());

    featureOffset = loadRanklib(featureStats, featuresById, labels, rhs, featureOffset);
    System.out.println("FeatureOffset: "+featureOffset);
    System.out.println("FeaturesSoFar: "+featureStats.keySet());

    // now for some normalization:
    for (int fid : featureStats.keySet()) {
      StreamingStats fstats = featureStats.get(fid);
      double mean = fstats.getMean();
      double stddev = fstats.getStandardDeviation();
      for (RanklibAtom ranklibAtom : featuresById.get(fid)) {
        double orig = ranklibAtom.featureValue;
        double normed = (orig - mean) / stddev;
        ranklibAtom.featureValue = (float) normed;
      }
    }

    Map<String, Map<String, PointBuilder>> groupedForOutput = new HashMap<>();
    Dataset outputDataset = new Dataset();
    for (List<RanklibAtom> ranklibAtoms : featuresById.values()) {
      for (RanklibAtom atom : ranklibAtoms) {
        PointBuilder pt = groupedForOutput
            // qid -> map
            .computeIfAbsent(atom.qid, mqid -> new HashMap<>())
            // doc -> pb
            .computeIfAbsent(atom.document, mdoc -> new PointBuilder(outputDataset).setDescription("# "+mdoc).setQID(atom.qid).setLabel(labels.getOrDefault(mdoc, 0f)));


        if(existsFeatures) {
          // set feature on pb
          pt.set(atom.featureId*2, 1f);
          pt.set(atom.featureId*2+1, atom.featureValue);
        } else {
          // set feature on pb
          pt.set(atom.featureId, atom.featureValue);
        }
      }
    }

    try(PrintWriter output = IO.openPrintWriter(argp.get("output", "ZScoreMerge."+(existsFeatures ? "exists." : "") +"ranklib"))) {
      groupedForOutput.forEach((qid, pts) -> {
        pts.forEach((doc, pb) -> {
          int maxFeature = outputDataset.getMaxFeaturePosition();
          if(!pb.hasFeature(maxFeature)) {
            pb.set(maxFeature, 0f);
          }
          DenseDataPoint ddp = pb.toDensePoint();
          output.println(ddp);
        });
      });
    }
  }

  private static int loadRanklib(HashMap<Integer, StreamingStats> featureStats, HashMap<Integer, List<RanklibAtom>> featuresById, HashMap<String, Float> labels, String lhsFile, int fidOffset) throws IOException {
    Dataset lhsDataset = new Dataset();
    try (LinesIterable lines = LinesIterable.fromFile(lhsFile)) {
      for (String line : lines) {
        try {
          PointBuilder pb = LibSVMFormat.parsePoint(line, lhsDataset);
          String qid = pb.getQID().intern();
          String doc = StrUtil.takeAfter(pb.getDescription(), "#").intern();
          if(pb.getLabel() > 0f) {
            labels.putIfAbsent(doc, pb.getLabel());
          }

          float[] data = pb.getRawFeatures();
          pb.getObservedFeatures().stream().forEach(fid -> {
            int nfid = fid + fidOffset;
            float val = data[fid];
            assert(!Float.isNaN(val));

            featureStats.computeIfAbsent(nfid, missing -> new StreamingStats()).push(val);
            featuresById.computeIfAbsent(nfid, missing -> new ArrayList<>()).add(new RanklibAtom(qid, doc, nfid, val));

          });
        } catch (RankLibError rle) {
          throw new IOException("Ranklib Error in "+lhsFile+":"+lines.getLineNumber());
        }
      }
    }
    return lhsDataset.getNextFeatureId() - 1 + fidOffset;
  }

}

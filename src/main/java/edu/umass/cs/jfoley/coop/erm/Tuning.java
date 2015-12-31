package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import org.lemurproject.galago.core.eval.QueryResults;
import org.lemurproject.galago.core.eval.QuerySetJudgments;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.core.eval.metric.QueryEvaluator;
import org.lemurproject.galago.core.eval.metric.QueryEvaluatorFactory;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class Tuning {
  public static List<List<Integer>> trainQs = new ArrayList<>(Arrays.asList(
      Arrays.asList(410, 312, 313, 357, 316, 317, 441, 365, 663, 400, 444, 302, 324, 368, 326, 348, 403, 425, 308, 649),
      Arrays.asList(351, 450, 353, 430, 311, 317, 441, 365, 663, 400, 444, 302, 324, 368, 326, 348, 403, 425, 308, 649),
      Arrays.asList(351, 450, 353, 430, 311, 410, 312, 313, 357, 316, 444, 302, 324, 368, 326, 348, 403, 425, 308, 649),
      Arrays.asList(351, 450, 353, 430, 311, 410, 312, 313, 357, 316, 317, 441, 365, 663, 400, 348, 403, 425, 308, 649),
      Arrays.asList(351, 450, 353, 430, 311, 410, 312, 313, 357, 316, 317, 441, 365, 663, 400, 444, 302, 324, 368, 326)
  ));
  public static List<List<Integer>> testQs = new ArrayList<>(Arrays.asList(
      Arrays.asList(351, 450, 353, 430, 311),
      Arrays.asList(410, 312, 313, 357, 316),
      Arrays.asList(317, 441, 365, 663, 400),
      Arrays.asList(444, 302, 324, 368, 326),
      Arrays.asList(348, 403, 425, 308, 649)
  ));

  public static List<KCVSplit> robustSplits = new ArrayList<>(Arrays.asList(
      new KCVSplit(trainQs.get(0), testQs.get(0)),
      new KCVSplit(trainQs.get(1), testQs.get(1)),
      new KCVSplit(trainQs.get(2), testQs.get(2)),
      new KCVSplit(trainQs.get(3), testQs.get(3)),
      new KCVSplit(trainQs.get(4), testQs.get(4))
  ));

  public static List<List<Integer>> ctrainQs = new ArrayList<>(Arrays.asList(
      Arrays.asList(255, 299, 212, 234, 213, 279, 236, 216, 239, 241, 220, 242, 243, 201, 223, 300, 202, 224, 268, 203, 204, 205, 227, 249, 206, 228, 207, 229, 208, 209),
      Arrays.asList(270, 230, 274, 297, 210, 211, 233, 216, 239, 241, 220, 242, 243, 201, 223, 300, 202, 224, 268, 203, 204, 205, 227, 249, 206, 228, 207, 229, 208, 209),
      Arrays.asList(270, 230, 274, 297, 210, 211, 233, 255, 299, 212, 234, 213, 279, 236, 223, 300, 202, 224, 268, 203, 204, 205, 227, 249, 206, 228, 207, 229, 208, 209),
      Arrays.asList(270, 230, 274, 297, 210, 211, 233, 255, 299, 212, 234, 213, 279, 236, 216, 239, 241, 220, 242, 243, 201, 205, 227, 249, 206, 228, 207, 229, 208, 209),
      Arrays.asList(270, 230, 274, 297, 210, 211, 233, 255, 299, 212, 234, 213, 279, 236, 216, 239, 241, 220, 242, 243, 201, 223, 300, 202, 224, 268, 203, 204)
  ));
  public static List<List<Integer>> ctestQs = new ArrayList<>(Arrays.asList(
      Arrays.asList(270, 230, 274, 297, 210, 211, 233),
      Arrays.asList(255, 299, 212, 234, 213, 279, 236),
      Arrays.asList(216, 239, 241, 220, 242, 243, 201),
      Arrays.asList(223, 300, 202, 224, 268, 203, 204),
      Arrays.asList(205, 227, 249, 206, 228, 207, 229, 208, 209)
  ));

  public static List<KCVSplit> clueSplits = new ArrayList<>(Arrays.asList(
      new KCVSplit(ctrainQs.get(0), ctestQs.get(0)),
      new KCVSplit(ctrainQs.get(1), ctestQs.get(1)),
      new KCVSplit(ctrainQs.get(2), ctestQs.get(2)),
      new KCVSplit(ctrainQs.get(3), ctestQs.get(3)),
      new KCVSplit(ctrainQs.get(4), ctestQs.get(4))
  ));

  public static class KCVSplit {
    final Set<String> train;
    final Set<String> test;
    public KCVSplit(List<Integer> train, List<Integer> test) {
      this.train = new HashSet<>(ListFns.map(train, x -> Integer.toString(x)));
      this.test = new HashSet<>(ListFns.map(test, x -> Integer.toString(x)));
    }
  }

  public static final int M_RM = 1;
  public static final int M_DRM = 2;
  public static final int M_GDRM = 3;
  public static final int M_GNB = 4;

  public static int PaperMethodNumber(String method) {
    switch (method) {
      case "wrm":
      case "rm": return M_RM;
      case "wgnb":
      case "gnb": return M_GNB;
      case "wgdrm":
      case "gdrm": return M_GDRM;
      case "wdrm":
      case "drm": return M_DRM;
      default: return -1;
    }
  }
  public static String PaperMethodFromNumber(int method) {
    switch (method) {
      case M_RM: return "RM";
      case M_GNB: return "GNB";
      case M_GDRM: return "GDRM";
      case M_DRM: return "DRM";
      default: return null;
    }
  }

  public static class RunDescriptor implements Comparable<RunDescriptor> {
    final String kb;
    final int method;
    final int numDocuments;

    public RunDescriptor(String kb, String method, int numDocuments) {
      this.kb = kb;
      this.method = PaperMethodNumber(method);
      this.numDocuments = numDocuments;
    }

    @Override
    public String toString() {
      return kb + "-"+PaperMethodFromNumber(method)+" k="+numDocuments;
    }

    @Override
    public int compareTo(@Nonnull RunDescriptor other) {
      int cmp = this.kb.compareTo(other.kb);
      if(cmp != 0) return cmp;
      cmp = Integer.compare(method, other.method);
      if(cmp != 0) return cmp;
      cmp = Integer.compare(numDocuments, other.numDocuments);
      return cmp;
    }

    @Override
    public int hashCode() {
      return kb.hashCode() ^ (method * numDocuments);
    }

    @Override
    public boolean equals(Object other) {
      if(other instanceof RunDescriptor) {
        RunDescriptor rhs = (RunDescriptor) other;
        return method == method && numDocuments == numDocuments && rhs.kb.equals(kb);
      }
      return false;
    }

    public Pair<String,Integer> getGroupKey() {
      return Pair.of(kb, method);
    }
  }

  public static <K,V> List<V> select(Iterable<? extends K> keys, Map<K,V> from) {
    ArrayList<V> output = new ArrayList<>();
    for (K key : keys) {
      V what = from.get(key);
      if(what != null) {
        output.add(what);
      }
    }
    return output;
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    //String dataset = argp.get("dataset", "clue12");
    //String qrels = argp.get("qrels", "clue12.mturk.qrel");
    String dataset = argp.get("dataset", "robust04");
    String qrels = argp.get("qrels", "robust.mturk.qrel");
    String measure = argp.get("metric", "map");
    QueryEvaluator scorer = QueryEvaluatorFactory.create(measure, argp);
    QueryEvaluator p5scorer = QueryEvaluatorFactory.create("P5", argp);
    Directory runDir = Directory.Read(argp.get("runs", "coop/ecir2016runs/sigir-dec30/raw/"));

    List<KCVSplit> splits = new ArrayList<>();
    switch (dataset) {
      case "robust04":
        splits.addAll(robustSplits);
        break;
      case "clue12":
        splits.addAll(clueSplits);
        break;
      default:
        throw new UnsupportedOperationException();
    }
    QuerySetJudgments judgments = new QuerySetJudgments(qrels);

    TreeMap<RunDescriptor, Map<String, Double>> runs = new TreeMap<>();
    TreeMap<RunDescriptor, QuerySetResults> results = new TreeMap<>();
    Map<Pair<String,Integer>, List<RunDescriptor>> groupedRuns = new HashMap<>();

    for (File file : runDir.children()) {
      final String name = file.getName();
      if(!name.contains(dataset)) continue;

      String[] parts = name.split("\\.");
      try {
        String kb = parts[0];
        String method = parts[1];
        String fdataset = parts[2];
        String ndocs = parts[3];
        int numDocs = Integer.parseInt(StrUtil.takeAfter(ndocs, "n"));

        String kbStyle = "title";
        if (method.startsWith("w")) {
          kbStyle = kb;
        }

        RunDescriptor rd = new RunDescriptor(kbStyle, method, numDocs);
        QuerySetResults qresults = new QuerySetResults(file.getAbsolutePath());
        Map<String, Double> scores = new HashMap<>();
        for (String qid : qresults.getQueryIterator()) {
          scores.put(qid, scorer.evaluate(qresults.get(qid), judgments.get(qid)));
        }
        runs.put(rd, scores);
        results.put(rd, qresults);
        MapFns.extendListInMap(groupedRuns, rd.getGroupKey(), rd);
      } catch (ArrayIndexOutOfBoundsException aerr) {
        continue;
      }
    }

    for (List<RunDescriptor> runDescriptors : groupedRuns.values()) {
      Collections.sort(runDescriptors);
      System.out.println(runDescriptors.get(0));
      List<TrainTestScore<RunDescriptor>> perSplitBest = new ArrayList<>();
      for (int i = 0; i < splits.size(); i++) {
        List<TrainTestScore<RunDescriptor>> rdesc = new ArrayList<>();
        for (RunDescriptor runDescriptor : runDescriptors) {
          Map<String, Double> scores = runs.get(runDescriptor);

          KCVSplit split = splits.get(i);
          StreamingStats stats = new StreamingStats();
          stats.pushAll(select(split.train, scores));

          StreamingStats testStats = new StreamingStats();
          testStats.pushAll(select(split.test, scores));
          //System.out.printf("\tTRAIN[%d]: %1.3f", i, stats.getMean());
          //System.out.printf("\tTEST[%d]: %1.3f\n", i, testStats.getMean());

          rdesc.add(new TrainTestScore<>(stats.getMean(), testStats.getMean(), runDescriptor));
        }
        rdesc.sort(Comparator.reverseOrder());
        perSplitBest.add(rdesc.get(0));
      }


      StreamingStats splitMAP = new StreamingStats();
      StreamingStats splitP5 = new StreamingStats();
      for (int i = 0; i < perSplitBest.size(); i++) {
        TrainTestScore<RunDescriptor> tts = perSplitBest.get(i);
        KCVSplit split = splits.get(i);
        splitMAP.push(tts.testScore);
        System.out.println("\tSelected: " + tts.obj + " TEST:" + String.format("%1.3f", tts.testScore)+" for: "+split.test);

        QuerySetResults trecrun = results.get(tts.obj);
        List<QueryResults> trainQueries = select(split.train, trecrun.toMap());
        Collections.sort(trainQueries);
        List<QueryResults> testQueries = select(split.test, trecrun.toMap());
        Collections.sort(testQueries);

        StreamingStats micro = new StreamingStats();
        for (QueryResults testQuery : testQueries) {
          micro.push(p5scorer.evaluate(testQuery, judgments.get(testQuery.getQuery())));
        }
        splitP5.push(micro.getMean());

        //String trainFile = String.format("%s.%s.split%d.train.trecrun", tts.obj.kb, PaperMethodFromNumber(tts.obj.method), i);
        //String testFile = String.format("%s.%s.split%d.test.trecrun", tts.obj.kb, PaperMethodFromNumber(tts.obj.method), i);
        String fullFile = String.format("%s.%s.split%d.trecrun", tts.obj.kb, PaperMethodFromNumber(tts.obj.method), i);
        try (PrintWriter out = IO.openPrintWriter(fullFile)) {
          for (QueryResults q : trainQueries) { q.outputTrecrun(out, tts.obj.kb); }
          for (QueryResults q : testQueries) { q.outputTrecrun(out, tts.obj.kb); }
        }
        //try (PrintWriter train = IO.openPrintWriter(trainFile)) {
        //  for (QueryResults trainQuery : trainQueries) {
        //    trainQuery.outputTrecrun(train, tts.obj.kb);
        //  }
        //}
        //try (PrintWriter test = IO.openPrintWriter(testFile)) {
        //  for (QueryResults testQuery : testQueries) {
        //    testQuery.outputTrecrun(test, tts.obj.kb);
        //  }
        //}
      }
      System.out.printf("%s MAP: %1.3f P5: %1.3f\n", runDescriptors.get(0), splitMAP.getMean(), splitP5.getMean());
    }

    /*runs.forEach((k, v) -> {
      System.out.println(k+" "+v);
    });*/

  }
  public static class TrainTestScore<T> implements Comparable<TrainTestScore<?>> {
    final double trainScore;
    final double testScore;
    private final T obj;

    public TrainTestScore(double trainScore, double testScore, T obj) {
      this.trainScore = trainScore;
      this.testScore = testScore;
      this.obj = obj;
    }

    @Override
    public int compareTo(@Nonnull TrainTestScore<?> o) {
      return Double.compare(this.trainScore, o.trainScore);
    }
  }
}

package edu.umass.cs.jfoley.coop.pagerank;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author jfoley
 */
public class PageRank {
  /** While debugging, make sure it doesn't run infinitely. */
  public static int MaxIterations = 1000;
  public static boolean printProgress = false;

  public static double[] calculatePageRank(NameMapping names, Neighbors neighbors, double lambda, double tau) {
    PageRankScores scores = new PageRankScores(names.size(), lambda);

    scores.initializeEvenChances(); // lines 6-8.

    for (int iter = 0; iter < MaxIterations; iter++) {
      if(printProgress) { System.err.printf("Start iteration %d\n", iter); }
      scores.initializeRandomSelection(); // lines 10-12.

      // For all pages:
      double weightToAddToEveryone = 0.0;
      for (int pageId = 0; pageId < names.size(); pageId++) {
        int[] nIds = neighbors.getOutlinks(pageId); // This is the set Q from the book; the pages p links to.

        double weight = (1-lambda) * scores.getCurrent(pageId);
        if(nIds.length == 0) {
          weightToAddToEveryone += weight / ((double) names.size()); // line 21, except deferred until the next loop.
        } else {
          // update all targets q in |Q|
          for (int q : nIds) {
            scores.incrementNext(q, weight / ((double) nIds.length)); // ((1-lambda)*Ip) / |P|
          }
        }
      }
      // Now, add the rank sink weight to everyone and calculate our total change:
      scores.incrementAll(weightToAddToEveryone);

      if(printProgress) { System.err.printf("Start rank-sink weight for iteration %d\n", iter); }

      // Check for convergence.
      // All done if our new pagerank vector isn't much bigger than our old vector.
      double l2 = scores.calculateL2();
      double l1 = scores.calculateL1();
      if(printProgress) { System.err.printf("End rank-sink weight for iteration %d, L2=%f, L1=%f, tau=%f\n", iter, l2, l1, tau); }
      if(l1 < tau) {
        return scores.getNextAsFinal();
      }

      scores.flipBuffers(); // swap next and current to go again. line 24, which should be outside line 25 in textbook :(
    }
    if(printProgress) { System.err.println("Finished page-rank"); }

    return scores.getNextAsFinal();
  }

  public static void main(String[] args) throws IOException {
    // Read arguments from command line; or use sane defaults for IDE.
    String inputFile = args.length >= 1 ? args[0] : "/mnt/scratch/jfoley/page-links_en.nt.bz2";
    double lambda = args.length >=2 ? Double.parseDouble(args[3]) : 0.15;
    double tau = args.length >=3 ? Double.parseDouble(args[4]) : 0.001;

    NameMapping names = NameMapping.load(inputFile);
    Neighbors neighbors = Neighbors.load(inputFile, names);

    double[] scores = calculatePageRank(names, neighbors, lambda, tau);
    printTopK(names, scores, 100);
  }

  private static void printTopK(NameMapping names, double[] scores, int K) {
    // A heap would be better, but Java doesn't have a great fixed-size heap implementation.
    ArrayList<ScoredDoc> scored = new ArrayList<>();
    for (int i = 0; i < names.size(); i++) {
      ScoredDoc doc = new ScoredDoc(scores[i], i);
      scored.add(doc);
      // Every once in a while, throw out the low-scoring documents.
      if(scored.size() % 5000 == 0) {
        Collections.sort(scored);
        ArrayList<ScoredDoc> top = new ArrayList<>(scored.subList(0, K));
        scored.clear();
        scored.addAll(top);
      }
    }

    // Finish by taking the topmost K.
    Collections.sort(scored);
    ArrayList<ScoredDoc> top = new ArrayList<>(scored.subList(0, K));
    scored.clear();

    // Output documents with the highest pagerank:
    for (ScoredDoc sdoc : top) {
      System.out.printf("%s\t%f\n", names.getName(sdoc.id), sdoc.score);
    }
  }


}

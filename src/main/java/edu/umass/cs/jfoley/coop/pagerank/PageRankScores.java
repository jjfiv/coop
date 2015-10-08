package edu.umass.cs.jfoley.coop.pagerank;

import java.util.Arrays;

/**
* @author jfoley
*/
public class PageRankScores {
  private final double lambda;
  private final int numPages;

  // Store arrays of current and next scores.
  double[] current;
  double[] next;

  public PageRankScores(int size, double lambda) {
    this.lambda = lambda;
    this.numPages = size;
    current = new double[size];
    next = new double[size];
  }

  public double getChanceOfRandomSelection() {
    return lambda / ((double) numPages);
  }

  /**
   * Move next scores to "current" scores for another iteration, and set all next scores to their starting value.
   * Nod to performance: don't make new arrays, just swap them and write over the older one :)
   */
  public void flipBuffers() {
    double[] tmp = current;
    current = next;
    next = tmp;
  }

  /** get the page at index's current (previous) score */
  public double getCurrent(int index) {
    return current[index];
  }

  /** update the page at index's next score by amount */
  public void incrementNext(int index, double amount) {
    next[index] += amount;
  }

  /** add a bit of pagerank to every element in the graph */
  public void incrementAll(double amount) {
    for (int pageId = 0; pageId < next.length; pageId++) {
      next[pageId] += amount;
    }
  }

  /** calculate the L1 distance between the current vector and the next vector */
  public double calculateL1() {
    double l1 = 0.0;
    for (int pageId = 0; pageId < next.length; pageId++) {
      double diff = next[pageId] - current[pageId];
      l1 += Math.abs(diff);
    }
    return l1;
  }

  /** calculate the L2 distance between the current vector and the next vector */
  public double calculateL2() {
    double L2Sum = 0.0;
    for (int pageId = 0; pageId < next.length; pageId++) {
      double diff = next[pageId] - current[pageId];
      L2Sum += diff*diff;
    }
    return Math.sqrt(L2Sum);
  }

  /** initialize the vector with even chances, once at the beginning. */
  public void initializeEvenChances() {
    // set up original scores to be even chances.
    Arrays.fill(current, 1.0 / ((double) numPages));
  }

  /** initialize the vector with random selection weights, once per iteration. */
  public void initializeRandomSelection() {
    // reset next array.
    Arrays.fill(next, getChanceOfRandomSelection());
  }

  /** get the "next" buffer and finish using this class */
  public double[] getNextAsFinal() {
    return next;
  }
}

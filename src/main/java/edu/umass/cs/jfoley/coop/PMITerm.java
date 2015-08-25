package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.lang.LazyPtr;
import org.lemurproject.galago.utility.Parameters;

/**
 * @author jfoley
 */
public class PMITerm implements Comparable<PMITerm> {
  public final String term;
  public final int termFrequency; // px, py
  public final int queryFrequency; // py
  public final int queryProxFrequency; // pxy
  public final double collectionLength;
  private final LazyPtr<Double> cachedPMI;

  public PMITerm(String term, int termFrequency, int queryFrequency, int queryProxFrequency, double collectionLength) {
    this.term = term;
    this.termFrequency = termFrequency;
    this.queryFrequency = queryFrequency;
    this.queryProxFrequency = queryProxFrequency;
    this.collectionLength = collectionLength;
    cachedPMI = new LazyPtr<>(this::computePMI);
  }

  public double px() {
    return termFrequency / collectionLength;
  }

  public double py() {
    return queryFrequency / collectionLength;
  }

  public double pxy() {
    return queryProxFrequency / collectionLength;
  }

  private double computePMI() {
    return pxy() / (px() * py());
  }

  public double pmi() {
    return cachedPMI.get();
  }

  @Override
  public int compareTo(PMITerm o) {
    return Double.compare(pmi(), o.pmi());
  }

  public Parameters toJSON() {
    Parameters tp = Parameters.create();
    tp.put("pmi", pmi());
    tp.put("tf", termFrequency);
    tp.put("qf", queryFrequency);
    tp.put("qpf", queryProxFrequency);
    tp.put("term", term);
    return tp;
  }
}

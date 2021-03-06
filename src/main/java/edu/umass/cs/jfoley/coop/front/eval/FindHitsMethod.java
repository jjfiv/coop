package edu.umass.cs.jfoley.coop.front.eval;

import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.List;

/**
 * @author jfoley
 */
public abstract class FindHitsMethod {
  public final Parameters output; // for taking notes on computation.

  public FindHitsMethod(Parameters input, Parameters output) {
    this.output = output;
  }

  public abstract List<DocumentResult<Integer>> compute() throws IOException;

  public List<DocumentResult<Integer>> computeTimed() throws IOException {
    long startTime = System.currentTimeMillis();
    List<DocumentResult<Integer>> hits = compute();
    long endTime = System.currentTimeMillis();
    int queryFrequency = hits.size();
    System.err.println("Found " +hits.size()+" hits in "+(endTime-startTime)+"ms.");
    output.put("queryFrequency", queryFrequency);
    output.put("queryTime", (endTime - startTime));
    return hits;
  }

  public abstract int getPhraseWidth();

  public abstract boolean queryContains(int term);
}

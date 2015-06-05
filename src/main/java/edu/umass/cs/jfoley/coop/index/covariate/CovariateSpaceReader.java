package edu.umass.cs.jfoley.coop.index.covariate;

import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.schema.DocVarSchema;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley
 */
public class CovariateSpaceReader<A extends Comparable<A>, B extends Comparable<B>> implements Closeable {
  public final IOMap<Covariable<A,B>, List<Integer>> reader;
  private final DocVarSchema<B> ySchema;
  private final DocVarSchema<A> xSchema;

  public CovariateSpaceReader(DocVarSchema<A> xSchema, DocVarSchema<B> ySchema, IOMap<Covariable<A, B>, List<Integer>> reader) {
    this.xSchema = xSchema;
    this.ySchema = ySchema;
    this.reader = reader;
  }

  public List<Integer> get(A aValue, B bValue) throws IOException {
    List<Integer> result = reader.get(new Covariable<>(aValue, bValue));
    if(result == null) return Collections.emptyList();
    return result;
  }

  public List<Integer> rangeQuery(List<A> xCandidates, List<B> yCandidates) {
    //ListFns.permutations(xCandidates, yCandidates);
    return null;
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}

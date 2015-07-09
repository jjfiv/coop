package edu.umass.cs.jfoley.coop.index.covariate;

import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.jfoley.coop.schema.DocVarSchema;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * @author jfoley
 */
public class CovariateSpaceReader<A, B> implements Closeable {
  public final IOMap<Covariable<A,B>, Mover> reader;
  private final DocVarSchema<B> ySchema;
  private final DocVarSchema<A> xSchema;

  public CovariateSpaceReader(DocVarSchema<A> xSchema, DocVarSchema<B> ySchema, IOMap<Covariable<A, B>, Mover> reader) {
    this.xSchema = xSchema;
    this.ySchema = ySchema;
    this.reader = reader;
  }

  public Mover get(A aValue, B bValue) throws IOException {
    return reader.get(new Covariable<>(aValue, bValue));
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

package edu.umass.cs.jfoley.coop.index.covariate;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.BufferList;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVar;
import edu.umass.cs.jfoley.coop.index.general.DocumentSetWriter;
import edu.umass.cs.jfoley.coop.index.general.IndexItemWriter;
import edu.umass.cs.jfoley.coop.schema.DocVarSchema;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

/**
 * Given two variables {@link DocVar}, create an IOMap that will allow for somewhat efficient cell-based lookup of documents that belong in that cell.
 * @author jfoley
 */
public class MapCovariateSpaceWriter<A extends Comparable<A>,B extends Comparable<B>> extends IndexItemWriter {

  private final DocVarSchema<A> xSchema;
  private final DocVarSchema<B> ySchema;
  private final DocumentSetWriter<Covariable<A,B>> writer;

  public static class Covariable<A extends Comparable<A>, B extends Comparable<B>> extends Pair<A,B> implements Comparable<Covariable<A,B>> {
    public Covariable(A left, B right) {
      super(left, right);
    }

    @Override
    public int compareTo(@Nonnull Covariable<A,B> o) {
      int cmp = left.compareTo(o.left);
      if(cmp != 0) return cmp;

      return right.compareTo(o.right);
    }
  }
  public static class CovariableCoder<A extends Comparable<A>, B extends Comparable<B>> extends Coder<Covariable<A,B>> {
    private Coder<A> xCoder;
    private Coder<B> yCoder;

    public CovariableCoder(Coder<A> xCoder, Coder<B> yCoder) {
      this.xCoder = xCoder;
      this.yCoder = yCoder;
    }

    @Override
    public boolean knowsOwnSize() {
      return true;
    }

    @Nonnull
    @Override
    public DataChunk writeImpl(Covariable<A,B> obj) throws IOException {
      BufferList bl = new BufferList();
      bl.add(xCoder, obj.left);
      bl.add(yCoder, obj.right);
      return bl.compact();
    }

    @Nonnull
    @Override
    public Covariable<A,B> readImpl(InputStream inputStream) throws IOException {
      A left = xCoder.readImpl(inputStream);
      B right = yCoder.readImpl(inputStream);
      return new Covariable<>(left, right);
    }
  }

  public MapCovariateSpaceWriter(Directory outputDir, IndexConfiguration cfg, DocVarSchema<A> xSchema, DocVarSchema<B> ySchema) throws IOException {
    super(outputDir, cfg);
    this.xSchema = xSchema;
    this.ySchema = ySchema;
    Coder<A> xCoder = xSchema.getCoder().lengthSafe();
    Coder<B> yCoder = ySchema.getCoder().lengthSafe();
    this.writer = new DocumentSetWriter<>(
        GalagoIO.getIOMapWriter(
            new CovariableCoder<>(xCoder, yCoder),
            new DeltaIntListCoder(),
            outputDir.childPath("covar." + xSchema.getName() + "." + ySchema.getName()), // TODO, do variables need short-names?
            Parameters.parseArray(
                "covarXSchema", xSchema.toJSON(),
                "covarYSchema", ySchema.toJSON()
            )
        )
    );
  }

  @Override
  public void process(CoopDoc document) throws IOException {
    // Skip documents that are missing either covariate.
    A varA = document.getVariableValue(xSchema);
    if(varA == null) return;
    B varB = document.getVariableValue(ySchema);
    if(varB == null) return;

    Covariable<A,B> covar = new Covariable<>(varA, varB);
    writer.process(covar, document.getIdentifier());
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}

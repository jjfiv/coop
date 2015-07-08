package edu.umass.cs.jfoley.coop.index.covariate;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVar;
import edu.umass.cs.jfoley.coop.index.general.DocumentSetWriter;
import edu.umass.cs.jfoley.coop.index.general.IndexItemWriter;
import edu.umass.cs.jfoley.coop.schema.DocVarSchema;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;

/**
 * Given two variables {@link DocVar}, create an IOMap that will allow for somewhat efficient cell-based lookup of documents that belong in that cell.
 * @author jfoley
 */
public class MapCovariateSpaceWriter<A, B> extends IndexItemWriter {

  private final DocVarSchema<A> xSchema;
  private final DocVarSchema<B> ySchema;
  private final DocumentSetWriter<Covariable<A,B>> writer;

  public MapCovariateSpaceWriter(Directory outputDir, IndexConfiguration cfg, DocVarSchema<A> xSchema, DocVarSchema<B> ySchema) throws IOException {
    super(outputDir, cfg);
    this.xSchema = xSchema;
    this.ySchema = ySchema;
    Coder<A> xCoder = xSchema.getCoder().lengthSafe();
    Coder<B> yCoder = ySchema.getCoder().lengthSafe();
    this.writer = new DocumentSetWriter<>(
        new CovariableCoder<>(xCoder, yCoder),
        GalagoIO.getRawIOMapWriter(
            outputDir.childPath("covar." + xSchema.getName() + "." + ySchema.getName()), // TODO, do variables need short-names?
            Parameters.parseArray(
                "covarXSchema", xSchema.toJSON(),
                "covarYSchema", ySchema.toJSON()
            )
        ).getSorting()
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

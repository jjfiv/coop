package edu.umass.cs.jfoley.coop.schema;

/**
 * @author jfoley
 */
public class CovariateSpaceConfiguration {
  public final DocVarSchema xSchema;
  public final DocVarSchema ySchema;

  public CovariateSpaceConfiguration(DocVarSchema xSchema, DocVarSchema ySchema) {
    this.xSchema = xSchema;
    this.ySchema = ySchema;
  }
}

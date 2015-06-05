package edu.umass.cs.jfoley.coop.schema;

import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import edu.umass.cs.jfoley.coop.tokenization.StanfordNLPTokenizer;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author jfoley
 */
public class IndexConfiguration {
  public CoopTokenizer tokenizer;
  public final Map<String, DocVarSchema> documentVariables;
  public final List<CovariateSpaceConfiguration> covariateSpaces;

  public IndexConfiguration(CoopTokenizer tokenizer, Map<String, DocVarSchema> documentVariables, List<CovariateSpaceConfiguration> covariateSpaces) {
    this.tokenizer = tokenizer;
    this.documentVariables = documentVariables;
    this.covariateSpaces = covariateSpaces;
  }

  @Nonnull
  public static IndexConfiguration fromMTEParameters(Parameters input) {
    // Load up var schema:
    Parameters schema = input.getMap("schema");
    Map<String,DocVarSchema> varSchemas = new HashMap<>();

    for (String fieldName : schema.keySet()) {
      String type = null;
      Parameters typeP = Parameters.create();
      if(schema.isString(fieldName)) {
        type = schema.getString(fieldName);
      } else if(schema.isMap(fieldName)) {
        typeP = schema.getMap(fieldName);
        type = typeP.getString("type");

      }
      if(type == null) {
        throw new RuntimeException("Can't figure out how to parse schema field: " + fieldName + " " + schema.get(fieldName));
      }

      typeP.set("type", type);
      varSchemas.put(fieldName, DocVarSchema.create(fieldName, typeP));
    }

    // Load up primary covariate-space:
    String xName = input.getString("x");
    String yName = input.getString("y");
    List<CovariateSpaceConfiguration> cvs = new ArrayList<>(Collections.singletonList(
        new CovariateSpaceConfiguration(varSchemas.get(xName), varSchemas.get(yName))
    ));

    return new IndexConfiguration(new StanfordNLPTokenizer(), varSchemas, cvs);
  }

  @Nonnull
  public static IndexConfiguration create() {
    return new IndexConfiguration(
        CoopTokenizer.create(),
        new HashMap<>(),
        new ArrayList<>()
    );
  }
}

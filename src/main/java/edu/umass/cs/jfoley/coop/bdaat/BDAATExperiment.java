package edu.umass.cs.jfoley.coop.bdaat;

import org.lemurproject.galago.core.retrieval.processing.MaxScoreDocumentModel;
import org.lemurproject.galago.core.retrieval.processing.ProcessingModel;
import org.lemurproject.galago.core.retrieval.processing.RankedDocumentModel;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley.
 */
public class BDAATExperiment {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    Map<String, Class<? extends ProcessingModel>> classesUnderTest = new HashMap<>();
    classesUnderTest.put("daat", RankedDocumentModel.class);
    classesUnderTest.put("maxscore-daat", MaxScoreDocumentModel.class);
    classesUnderTest.put("bdaat", BDAATProcessingModel.class);

    String model = argp.getString("model");

    String name = classesUnderTest.get(model).getName();

    Parameters qp = Parameters.create();
    qp.put("processingModel", name);

  }
}

package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import edu.umass.cs.jfoley.coop.document.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.document.schema.IntegerVarSchema;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class BuildIndexMTE extends AppFunction {
  @Override
  public String getName() {
    return "build-index-mte";
  }

  @Override
  public String getHelpString() {
    return makeHelpStr(
        "input", "A directory with *.conf that might be passed to MTE",
        "output", "Where to save our index."
    );
  }

  @Override
  public void run(Parameters argp, PrintStream output) throws Exception {
    Directory inputDir = new Directory(argp.getString("input"));
    Directory outputDir = new Directory(argp.getString("output"));

    Config conf = null;
    for (File file : inputDir.children()) {
      if(file.getName().endsWith(".conf")) {
        conf = ConfigFactory.parseFile(file);
      }
    }
    if(conf == null) throw new RuntimeException("Couldn't find a config file that describes the schema and dataset in "+inputDir);

    // Yep, stop using their library now.
    Parameters dataset = Parameters.parseString(conf.root().render(ConfigRenderOptions.concise()));

    // pull out data or die!
    String data = dataset.getString("data");
    File dataFile = inputDir.child(data);
    File nerFile = inputDir.child(data);

    if (!dataFile.exists() && !nerFile.exists()) {
      throw new RuntimeException("Expected a data file or a ner file.");
    }

    // Load up var schema:
    Parameters schema = dataset.getMap("schema");
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

      switch (type) {
        case "categorical":
          varSchemas.put(fieldName, CategoricalVarSchema.create(fieldName, typeP));
          break;
        case "numeric:":
          varSchemas.put(fieldName, new IntegerVarSchema(fieldName));
          break;
      }
    }
    System.out.println(varSchemas);

    if(dataFile.exists()) {
      buildIndexFromDataFile(argp, varSchemas, dataFile, outputDir);
    } else {
      throw new UnsupportedOperationException("JustNERFile");
    }

  }

  public static void buildIndexFromDataFile(Parameters argp, Map<String, DocVarSchema> varSchemas, File dataFile, Directory outputDir) throws IOException {
    CoopTokenizer tok = CoopTokenizer.create(argp);
    try(IndexBuilder builder = new IndexBuilder(tok, outputDir)) {
      try (LinesIterable input = LinesIterable.fromFile(dataFile)) {
        for (String line : input) {
          Parameters data = Parameters.parseFile(line);
          CoopDoc doc = CoopDoc.createMTE(tok, data, varSchemas);
          doc.setRawText(data.toString());
          builder.addDocument(doc);
        }
      }
    }
  }
}

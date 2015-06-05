package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.MTECoopDoc;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

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

    IndexConfiguration config = IndexConfiguration.fromMTEParameters(dataset);

    if(dataFile.exists()) {
      buildIndexFromDataFile(config, dataFile, outputDir);
    } else {
      throw new UnsupportedOperationException("JustNERFile");
    }

  }

  public static void buildIndexFromDataFile(IndexConfiguration cfg, File dataFile, Directory outputDir) throws IOException {
    try(IndexBuilder builder = new IndexBuilder(cfg, outputDir)) {
      try (LinesIterable input = LinesIterable.fromFile(dataFile)) {
        for (String line : input) {
          Parameters data = Parameters.parseString(line);
          CoopDoc doc = MTECoopDoc.createMTE(cfg, data);
          doc.setRawText(data.toString());
          builder.addDocument(doc);
        }
      }
    }
  }
}

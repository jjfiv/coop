package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.DocumentSource;
import org.lemurproject.galago.core.parse.DocumentStreamParser;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

/**
 * @author jfoley.
 */
public class BuildIndex extends AppFunction {

  @Override
  public String getName() {
    return "build-index";
  }

  @Override
  public String getHelpString() {
    return makeHelpStr(
        "input", "input files",
        "output", "output directory to store index");
  }

  @Override
  public void run(Parameters argp, PrintStream output) throws Exception {
    List<DocumentSplit> splits = DocumentSource.processFile(new File(argp.getString("input")), argp);
    try (VocabBuilder builder = new VocabBuilder(new Directory(argp.getString("output")))) {

      int x = 0;
      for (DocumentSplit split : splits) {
        try (DocumentStreamParser parser = DocumentStreamParser.create(split, argp)) {
          while(true) {
            Document doc = parser.nextDocument();
            if(doc == null) break;
            if((x++ % 1000) == 0) {
              System.out.println("+doc:\t" + x + "\t" + doc.name + "\t" + StrUtil.preview(doc.text, 60));
            }
            builder.addDocument(doc.name, doc.text);
          }
        }
      }
    }
  }
}

package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.errors.FatalError;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.index.VocabReader;
import edu.umass.cs.jfoley.coop.querying.DocumentAndPosition;
import edu.umass.cs.jfoley.coop.querying.LocatePhrase;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.tokenize.Tokenizer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley.
 */
public class PhraseFinder extends AppFunction {
  @Override
  public String getName() {
    return "phrase-finder";
  }

  @Override
  public String getHelpString() {
    return makeHelpStr(
        "index", "path to VocabReader index.",
        "width", "kwic width [default=5]",
        "limit", "limit the number of results: [default=10,000]",
        "query", "a term or phrase query");
  }

  @Override
  public void run(Parameters p, PrintStream output) throws Exception {
    int width = p.get("width", 5);
    int limit = p.get("limit", 10000);

    try (VocabReader index = new VocabReader(new Directory(p.getString("index")))) {
      Tokenizer tokenizer = new TagTokenizer();
      List<String> query = tokenizer.tokenize(p.getString("query")).terms;

      Pair<Long, List<DocumentAndPosition>> hits = Timing.milliseconds(() -> LocatePhrase.find(index, query));
      System.err.println("Run query in "+hits.left+" ms. "+hits.right.size()+" hits found!");

      List<TermSlice> slices = new ArrayList<>();
      for (DocumentAndPosition hit : ListFns.take(hits.right, limit)) {
        slices.add(hit.asSlice(width));
      }

      Pair<Long, List<Pair<TermSlice, List<String>>>> kwic = Timing.milliseconds(() -> {
        try {
          return index.pullTermSlices(slices);
        } catch (IOException e) {
          throw new FatalError(e);
        }
      });

      System.err.println("Pull result data in "+kwic.left+" ms.");

      for (Pair<TermSlice, List<String>> data : kwic.right) {
        TermSlice info = data.left;
        System.out.printf("%03d:[%03d,%03d]= ", info.document, info.start, info.end);
        System.out.println(StrUtil.join(data.right, " "));
      }

    }
  }

}

package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.build.CorpusNumberer;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;
import org.lemurproject.galago.utility.json.JSONUtil;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;

/**
 * @author jfoley
 */
public class ByMonthTextIndexer {
  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory output = new Directory(argp.get("output", "/mnt/scratch/jfoley/nyt-clinton.ints"));

    Debouncer msg = new Debouncer(3000);

    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();

    StreamingStats tokenizationTime = new StreamingStats();
    StreamingStats processTime = new StreamingStats();
    try (CorpusNumberer.IntCorpusWriter writer = new CorpusNumberer.IntCorpusWriter(output);
         LinesIterable lines = LinesIterable.fromFile("nyt-clinton.tsv.gz")) {

      for (String line : lines) {
        String dat[] = line.split("\t");
        String docName = StrUtil.takeAfter(dat[0], ":"); // drop file from grep
        // remove quotes and unescape:
        String title = JSONUtil.unescape(StrUtil.removeSurrounding(dat[1], "\"", "\""));
        String body = JSONUtil.unescape(StrUtil.removeSurrounding(dat[2], "\"", "\""));

        long st, et;
        Document doc = new Document();
        doc.name = docName;
        doc.metadata.put("title", title);
        doc.text = body;

        st = System.nanoTime();
        tokenizer.tokenize(doc);
        et = System.nanoTime();
        tokenizationTime.push((et -st) / 1e9);

        if(msg.ready()) {
          System.out.println(msg.estimate(writer.nextDocId, 17803));
          System.out.println("# "+doc.name+" "+writer.nextDocId);
          System.out.println("\t"+title+" ");
          System.out.println("\t"+StrUtil.preview(body, 100));
          System.out.println("\t tokenizing: "+tokenizationTime);
          System.out.println("\t processing : "+processTime);
        }
        st = System.nanoTime();
        writer.process(doc);
        et = System.nanoTime();
        processTime.push((et - st) / 1e9);
      }
    }

    System.out.println("# Finished.");
    System.out.println("\t tokenizing: "+tokenizationTime);
    System.out.println("\t processing : "+processTime);

  }
}

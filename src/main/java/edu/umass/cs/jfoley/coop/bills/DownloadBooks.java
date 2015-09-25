package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.io.ShardedTextWriter;
import ciir.jfoley.chai.time.Debouncer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * @author jfoley.
 */
public class DownloadBooks {
  public static String urlForInternetArchiveText(String id, String kind) {
    return "https://archive.org/download/"+id+"/"+id+"_djvu."+kind;
  }

  public static String TREC(String name, String text) {
    return "<DOC>\n"
        + "<DOCNO>"+name+"</DOCNO>\n"
        + "<TEXT>\n"
        + text
        + "</TEXT>\n"
        + "</DOC>\n";
  }

  public static void main(String[] args) throws URISyntaxException, IOException {
    Parameters argp = Parameters.parseArgs(args);
    ArrayList<String> ids = new ArrayList<>();

    try(LinesIterable lines = LinesIterable.fromFile(argp.get("input", "book-ids.txt"))) {
      for (String line : lines) {
        ids.add(line.trim());
      }
    }
    String kind = argp.get("kind", "txt");

    Debouncer msg = new Debouncer(10000);
    try (ShardedTextWriter trectext = new ShardedTextWriter(new Directory(argp.get("output", "inex_txt")), "books", "trectext.gz", 2000)) {
      for (int i = 0; i < ids.size(); i++) {
        String id = ids.get(i);
        URI url = new URI(urlForInternetArchiveText(id, kind));
        try (InputStream stream = url.toURL().openStream()) {
          String body = IO.slurp(stream);
          System.err.println(id + " "+ msg.estimate(i, ids.size()));
          trectext.process(TREC(id, body));
        } catch (IOException e) {
          System.err.println(id);
          e.printStackTrace(System.err);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }



  }
}

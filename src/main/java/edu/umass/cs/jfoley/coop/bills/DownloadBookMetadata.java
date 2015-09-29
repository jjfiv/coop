package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.io.archive.ZipWriter;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.util.HTTP;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jfoley.
 */
public class DownloadBookMetadata {
  @Nonnull
  public static String urlForInternetArchiveMetadata(String id) {
    return "https://archive.org/download/"+id+"/"+id+"_meta.xml";
  }

  public static void main(String[] args) throws URISyntaxException, IOException {
    Parameters argp = Parameters.parseArgs(args);
    ArrayList<String> ids = new ArrayList<>();

    try(LinesIterable lines = LinesIterable.fromFile(argp.get("input", "book-ids.txt"))) {
      for (String line : lines) {
        ids.add(line.trim());
      }
    }

    Debouncer msg = new Debouncer(1000);
    AtomicInteger count = new AtomicInteger(0);
    try (ZipWriter writer = new ZipWriter(argp.getString("output"))) {
      ids.parallelStream().unordered().forEach((id) -> {
        try {
          HTTP.Response response = HTTP.get(urlForInternetArchiveMetadata(id));
          if (response.status == 200) {
            synchronized (writer) {
              writer.writeUTF8(id + ".xml", response.body);
            }
          }
        } catch (IOException e) {
          System.err.println(id);
          e.printStackTrace(System.err);
        }

        // stats:
        int total = count.incrementAndGet();
        if (msg.ready()) {
          synchronized (System.err) {
            System.err.println(id + " " +total+"/"+ids.size()+" "+ msg.estimate(total, ids.size()));
          }
        }
      });
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

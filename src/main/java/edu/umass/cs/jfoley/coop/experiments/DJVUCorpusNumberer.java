package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.inputs.InputContainer;
import ciir.jfoley.chai.io.inputs.InputFinder;
import ciir.jfoley.chai.io.inputs.InputStreamable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.files.FileSink;
import gnu.trove.list.array.TShortArrayList;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class DJVUCorpusNumberer {
  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory output = new Directory(argp.get("output", "inex-page-djvu.ints"));

    StringPooler.disable();
    InputFinder finder = InputFinder.Default();

    int totalPages = 0;
    int totalBooks = 0;
    long st, et;
    StreamingStats parsingTime = new StreamingStats();
    StreamingStats processTime = new StreamingStats();
    Debouncer msg = new Debouncer(3000);
    try (CorpusNumberer.IntCorpusWriter writer = new CorpusNumberer.IntCorpusWriter(output);
         FileSink coordsWriter = new FileSink(output.child("coords"))
    ) {
      for (InputContainer archive : finder.findAllInputs(argp.get("input", "/mnt/scratch/jfoley/inex-djvuxml.zip"))) {
        for (InputStreamable is : archive.getInputs()) {
          st = System.nanoTime();
          List<DJVUPageParser.DJVUPage> pages = DJVUPageParser.parseDJVU(is.getInputStream(), is.getName());
          et = System.nanoTime();
          parsingTime.push((et - st) / 1e9);
          if(pages.isEmpty()) continue;

          totalPages += pages.size();
          totalBooks++;

          if(msg.ready()) {
            System.out.println(msg.estimate(totalBooks, 50200));
            System.out.println("Parsed: " + pages.size() + " pages from " + is.getName());
            System.out.println("pages/s: " + msg.estimate(totalPages));
            System.out.println("# "+is.getName()+" "+writer.nextDocId);
            System.out.println("\t parse  : "+parsingTime);
            System.out.println("\t process: "+processTime);
          }

          for (DJVUPageParser.DJVUPage page : pages) {
            String name = page.archiveId+":"+page.pageNumber;
            List<String> terms = new ArrayList<>(page.size());
            TShortArrayList coords = new TShortArrayList(pages.size()*4);
            for (DJVUPageParser.DJVUWord word : page.words) {
              int count = word.terms.size();
              terms.addAll(word.terms);
              // copy coords to all tokens made by our tokenizer.
              for (int i = 0; i < count; i++) {
                coords.addAll(word.coords);
              }
            }

            // process document:
            st = System.nanoTime();
            writer.process(name, terms);
            et = System.nanoTime();
            processTime.push((et - st) / 1e9);

            // 2 bytes per coordinate:
            ByteBuffer cdata = ByteBuffer.allocate(coords.size() * 2);
            for (int i = 0; i < coords.size(); i++) {
              cdata.putShort(i * 2, coords.getQuick(i));
            }
            coordsWriter.write(cdata);
          }

        }
      }
    }

    System.out.println("# Finished.");
    System.out.println("\t parse     : "+parsingTime);
    System.out.println("\t processing : "+processTime);
  }
}

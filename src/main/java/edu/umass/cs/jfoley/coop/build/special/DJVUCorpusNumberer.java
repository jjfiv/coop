package edu.umass.cs.jfoley.coop.build.special;

import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.inputs.InputContainer;
import ciir.jfoley.chai.io.inputs.InputFinder;
import ciir.jfoley.chai.io.inputs.InputStreamable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.files.FileSink;
import edu.umass.cs.jfoley.coop.build.CorpusNumberer;
import gnu.trove.list.array.TShortArrayList;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jfoley
 */
public class DJVUCorpusNumberer {
  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory output = new Directory(argp.get("output", "/mnt/scratch/jfoley/inex-page-djvu.ints"));

    StringPooler.disable();
    InputFinder finder = InputFinder.Default();

    AtomicInteger totalPages = new AtomicInteger(0);
    AtomicInteger totalBooks = new AtomicInteger(0);
    StreamingStats parsingTime = new StreamingStats();
    StreamingStats processTime = new StreamingStats();
    Debouncer msg = new Debouncer(3000);

    try (CorpusNumberer.IntCorpusWriter writer = new CorpusNumberer.IntCorpusWriter(output);
         FileSink coordsWriter = new FileSink(output.child("coords"))
    ) {
      for (InputContainer archive : finder.findAllInputs(argp.get("input", "/mnt/scratch/jfoley/inex-djvuxml.zip"))) {
        assert(archive.isParallel());
        List<InputStreamable> entries = IterableFns.intoList(archive.getInputs());
        entries.parallelStream().map((is) -> {
          long st = System.nanoTime();
          List<DJVUPageParser.DJVUPage> pages = Collections.emptyList();
          try {
            pages = DJVUPageParser.parseDJVU(is.getInputStream(), is.getName());
          } catch (IOException e) {
            return pages;
          } catch (OutOfMemoryError oom) {
            return pages;
          }
          long et = System.nanoTime();
          parsingTime.push((et - st) / 1e9);

          int np = totalPages.addAndGet(pages.size());
          int nb = totalBooks.incrementAndGet();

          if (msg.ready()) {
            System.out.println(msg.estimate(nb, 50200));
            System.out.println("Parsed: " + pages.size() + " pages from " + is.getName());
            System.out.println("pages/s: " + msg.estimate(np));
            System.out.println("# " + is.getName() + " " + writer.nextDocId);
            System.out.println("\t parse  : " + parsingTime);
            System.out.println("\t process: " + processTime);
          }
          return pages;
        }).sequential().forEach((pages) -> {
          if (pages.isEmpty()) {
            return;
          }
          for (DJVUPageParser.DJVUPage page : pages) {
            String name = page.archiveId + ":" + page.pageNumber;
            List<String> terms = new ArrayList<>(page.size());
            TShortArrayList coords = new TShortArrayList(pages.size() * 4);
            for (DJVUPageParser.DJVUWord word : page.words) {
              int count = word.terms.size();
              terms.addAll(word.terms);
              // copy coords to all tokens made by our tokenizer.
              for (int i = 0; i < count; i++) {
                coords.addAll(word.coords);
              }
            }

            try {
              // process document:
              long st = System.nanoTime();
              writer.process(name, terms);
              long et = System.nanoTime();
              processTime.push((et - st) / 1e9);

              // 2 bytes per coordinate:
              ByteBuffer cdata = ByteBuffer.allocate(coords.size() * 2);
              for (int i = 0; i < coords.size(); i++) {
                cdata.putShort(i * 2, coords.getQuick(i));
              }
              coordsWriter.write(cdata);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }

        });
      }
    }

    System.out.println("# Finished.");
    System.out.println("\t parse     : "+parsingTime);
    System.out.println("\t processing : "+processTime);
  }
}

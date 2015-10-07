package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.io.archive.ZipWriter;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.DocumentSource;
import org.lemurproject.galago.core.parse.DocumentStreamParser;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

/**
 * @author jfoley
 */
public class TrecTextToUniqueZip {
  public static void main(String[] args) throws IOException {
  Parameters argp = Arguments.parse(args);
  File output = new File(argp.get("output", "/mnt/scratch/jfoley/dbpedia.zip"));
  //List<DocumentSplit> documentSplits = DocumentSource.processDirectory(new File(argp.get("input", "inex_txt")), argp);

  List<DocumentSplit> documentSplits = DocumentSource.processDirectory(new File(argp.get("input", "/mnt/scratch/jfoley/dbpedia.trectext")), argp);

  Debouncer msg = new Debouncer(3000);

  HashSet<String> alreadySeenDocuments = new HashSet<>();

  StreamingStats parsingTime = new StreamingStats();
  StreamingStats processTime = new StreamingStats();
  int skipped = 0;
  int total = 0;
  try (ZipWriter writer = new ZipWriter(output.getAbsolutePath())) {
    for (DocumentSplit documentSplit : documentSplits) {
      DocumentStreamParser parser = DocumentStreamParser.create(documentSplit, argp);

      long st, et;
      while (true) {
        st = System.nanoTime();
        Document doc = parser.nextDocument();
        if (doc == null) break;
        et = System.nanoTime();
        parsingTime.push((et - st) / 1e9);

        if(alreadySeenDocuments.contains(doc.name)) {
          skipped++;
          continue;
        }
        alreadySeenDocuments.add(doc.name);

        // name prefix
        doc.text = doc.name.replace('_', ' ') + ":\n" + doc.text;


        int id = total++;
        if(msg.ready()) {
          System.out.println(msg.estimate(id, 10_000_000));
          System.out.println("# "+doc.name+" "+id);
          System.out.println("\t skipped     : "+skipped);
          System.out.println("\t parse     : "+parsingTime);
          System.out.println("\t processing : "+processTime);
        }
        st = System.nanoTime();
        writer.writeUTF8(doc.name, doc.text);
        et = System.nanoTime();
        processTime.push((et - st) / 1e9);
      }
    }
  }

  System.out.println("# Finished.");
  System.out.println("\t parse     : "+parsingTime);
  System.out.println("\t processing : "+processTime);

}
}

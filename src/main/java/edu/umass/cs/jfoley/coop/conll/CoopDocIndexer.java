package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.archive.ZipArchive;
import ciir.jfoley.chai.io.archive.ZipArchiveEntry;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class CoopDocIndexer {
  public static void main(String[] args) throws IOException {
    Directory output = new Directory("clue_pre5.index");
    List<File> inputZips = new ArrayList<>();
    List<File> candidates = Directory.Read(".").children();
    for (File candidate : candidates) {
      if(candidate.getName().startsWith("clue_schemax")) {
        inputZips.add(candidate);
      }
    }
    KryoCoder<CoopDoc> coder = new KryoCoder<>(CoopDoc.class);

    Debouncer msg = new Debouncer(1000);

    long startTime = System.currentTimeMillis();
    try (TermBasedIndexWriter builder = new TermBasedIndexWriter(output)) {
      for (File inputZip : inputZips) {
        try (ZipArchive zip = ZipArchive.open(inputZip)) {
          List<ZipArchiveEntry> listEntries = zip.listEntries();
          for (int i = 0; i < listEntries.size(); i++) {
            ZipArchiveEntry entry = listEntries.get(i);
            CoopDoc doc = coder.read(entry.getInputStream());
            if(msg.ready()) {
              System.err.println(i + "/" + listEntries.size() + " " + doc.getName());
              System.err.println("# "+msg.estimate(i, listEntries.size()));
            }
            builder.addDocument(doc);
            if(i >= 50) break;
          }
        }
      }
      long endParsingTime = System.currentTimeMillis();
      System.out.println("Total parsing time: "+(endParsingTime - startTime)+"ms.");
    }
    long endTime = System.currentTimeMillis();
    System.out.println("Total time: "+(endTime - startTime)+"ms.");
    // 27.6s i>=50

  }
}

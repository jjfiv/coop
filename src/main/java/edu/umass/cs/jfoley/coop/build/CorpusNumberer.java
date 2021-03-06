package edu.umass.cs.jfoley.coop.build;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.files.FileSink;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.DocumentSource;
import org.lemurproject.galago.core.parse.DocumentStreamParser;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;

/**
 * This takes an input corpus and converts it to a vocabulary-numbered version.
 * The corpus file is a flat file of integer ids of terms in order.
 * The docs mapping contains names to index mappings for the documents
 * The offsets file is a mapping of indexes that mark the start of a document in the corpus int[]; isa long[]
 * @author jfoley
 */
public class CorpusNumberer {

  public static class IntCorpusWriter implements Closeable {
    FileSink corpusWriter; // int[] points to vocabulary
    FileSink docOffsetWriter; // long[] points to corpusWriter
    IdMaps.Writer<String> docNames; // index points to docOffsetWriter
    IdMaps.Writer<String> vocabulary;

    public int nextDocId = 0;
    // reserve zero just in case
    int nextTermId = 1;
    TObjectIntHashMap<String> memVocab;

    public IntCorpusWriter(Directory output) throws IOException {
      corpusWriter = new FileSink(output.child("intCorpus"));
      docOffsetWriter = new FileSink(output.child("docOffset"));
      vocabulary = GalagoIO.openIdMapsWriter(
          output.childPath("vocab"),
          FixedSize.ints,
          CharsetCoders.utf8
      );
      docNames = GalagoIO.openIdMapsWriter(
          output.childPath("names"),
          FixedSize.ints,
          CharsetCoders.utf8
      );

      memVocab = new TObjectIntHashMap<>();
    }

    public void process(Document gdoc) throws IOException {
      process(gdoc.name, gdoc.terms);
    }

    public void process(String docName, List<String> terms) throws IOException {
      int docId = IntMath.fromLong(docOffsetWriter.tell() / 8L);
      docNames.put(docId, docName);
      nextDocId++;
      assert(docId == nextDocId);

      long documentStart = corpusWriter.tell();
      docOffsetWriter.write(FixedSize.longs, documentStart);

      ByteBuffer ints = ByteBuffer.allocate(terms.size()*4);
      //IntList data = new IntList(terms.size());
      for (String term : terms) {
        int tid = getTermId(term);
        //data.add(tid);
        ints.putInt(tid);
      }
      ints.rewind();
      corpusWriter.write(ints);
      //corpusWriter.write(FixedSize.ints, tid);
    }

    public int getTermId(String term) throws IOException {
      int tid = memVocab.get(term);
      // allocate new term:
      if (tid == memVocab.getNoEntryValue()) {
        tid = nextTermId++;

        // add to memory and disk vocab:
        memVocab.put(term, tid);
        vocabulary.put(tid, term);
      }
      return tid;
    }

    @Override
    public void close() throws IOException {
      corpusWriter.close();
      docOffsetWriter.close();
      vocabulary.close();
      docNames.close();
    }
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory output = new Directory(argp.get("output", "/mnt/scratch3/jfoley/w3c.ints"));
    //List<DocumentSplit> documentSplits = DocumentSource.processDirectory(new File(argp.get("input", "inex_txt")), argp);

    List<DocumentSplit> documentSplits = DocumentSource.processFile(new File(argp.get("input", "/mnt/scratch3/jfoley/w3c/")), argp);
    //Directory output = new Directory(argp.get("output", "dbpedia.ints"));
    //List<DocumentSplit> documentSplits = DocumentSource.processDirectory(new File(argp.get("input", "/mnt/scratch/jfoley/dbpedia.trectext")), argp);

    boolean namePrefix = argp.get("namePrefix", false);

    Debouncer msg = new Debouncer(3000);
    final long total = 331_037; // w3c corpus

    HashSet<String> alreadySeenDocuments = new HashSet<>();

    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();

    StreamingStats parsingTime = new StreamingStats();
    StreamingStats tokenizationTime = new StreamingStats();
    StreamingStats processTime = new StreamingStats();
    int skipped = 0;
    try (IntCorpusWriter writer = new IntCorpusWriter(output)) {
      for (DocumentSplit documentSplit : documentSplits) {
        DocumentStreamParser parser = DocumentStreamParser.create(documentSplit, argp);

        long st, et;
        while (true) {
          st = System.nanoTime();
          Document doc = parser.nextDocument();
          if (doc == null) break;
          et = System.nanoTime();
          parsingTime.push((et - st) / 1e9);

          // patch name!
          //String book = doc.metadata.get("book");
          //String sentence = doc.metadata.get("sentence");
          //String year = doc.metadata.get("year");
          //doc.name = book+"-"+sentence+":"+year;

          if(alreadySeenDocuments.contains(doc.name)) {
            skipped++;
            continue;
          }

          if(namePrefix) {
            doc.text = doc.name.replace('_', ' ') + '\n' + doc.text;
          }

          alreadySeenDocuments.add(doc.name);
          st = System.nanoTime();
          tokenizer.tokenize(doc);
          et = System.nanoTime();
          tokenizationTime.push((et -st) / 1e9);


          if(msg.ready()) {
            System.out.println(msg.estimate(writer.nextDocId, total));
            System.out.println("# "+doc.name+" "+writer.nextDocId);
            System.out.println("\t skipped     : "+skipped);
            System.out.println("\t parse     : "+parsingTime);
            System.out.println("\t tokenizing: "+tokenizationTime);
            System.out.println("\t processing : "+processTime);
          }
          st = System.nanoTime();
          writer.process(doc);
          et = System.nanoTime();
          processTime.push((et - st) / 1e9);
        }
      }
    }

    System.out.println("# Finished.");
    System.out.println(msg.estimate(total, total));
    System.out.println("\t parse     : "+parsingTime);
    System.out.println("\t tokenizing: "+tokenizationTime);
    System.out.println("\t processing : "+processTime);

  }
}

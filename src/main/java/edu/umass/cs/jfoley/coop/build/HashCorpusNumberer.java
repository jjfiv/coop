package edu.umass.cs.jfoley.coop.build;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.files.FileSink;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import org.lemurproject.galago.contrib.hash.UniversalStringHashFunction;
import org.lemurproject.galago.core.index.disk.DiskNameReader;
import org.lemurproject.galago.core.index.disk.PositionIndexReader;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.DocumentSource;
import org.lemurproject.galago.core.parse.DocumentStreamParser;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.parse.stem.KrovetzStemmer;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * @author jfoley
 */
public class HashCorpusNumberer {

  public static class HashIntCorpusWriter implements Closeable {
    private final UniversalStringHashFunction hasher;
    private final Directory output;
    FileSink corpusWriter; // int[] points to vocabulary
    FileSink docOffsetWriter; // long[] points to corpusWriter
    IdMaps.Writer<String> docNames; // index points to docOffsetWriter

    int nextDocId = 0;

    public HashIntCorpusWriter(Directory output, UniversalStringHashFunction hasher) throws IOException {
      this.output = output;
      this.hasher = hasher;
      corpusWriter = new FileSink(output.child("intCorpus"));
      docOffsetWriter = new FileSink(output.child("docOffset"));
      docNames = GalagoIO.openIdMapsWriter(
          output.childPath("names"),
          FixedSize.ints,
          CharsetCoders.utf8
      );
    }

    public void process(Document gdoc) throws IOException {
      process(gdoc.name, gdoc.terms);
    }

    public void process(String docName, List<String> terms) throws IOException {
      int docId = IntMath.fromLong(docOffsetWriter.tell() / 8L);
      docNames.put(docId, docName);
      assert(docId == nextDocId++);

      long documentStart = corpusWriter.tell();
      docOffsetWriter.write(FixedSize.longs, documentStart);

      ByteBuffer ints = ByteBuffer.allocate(terms.size()*4);
      //IntList data = new IntList(terms.size());
      for (String term : terms) {
        //int tid = (int) ((hasher.hash(term)) & 0xffffffffL);
        int tid = term.hashCode();
        ints.putInt(tid);
      }
      ints.rewind();
      corpusWriter.write(ints);
      //corpusWriter.write(FixedSize.ints, tid);
    }

    @Override
    public void close() throws IOException {
      IO.spit(hasher.toParameters().toPrettyString(), output.child("hash.json"));
      corpusWriter.close();
      docOffsetWriter.close();
      docNames.close();
    }
  }

  public static void main(String[] args) throws Exception {
    Parameters argp = Arguments.parse(args);
    Directory output = new Directory(argp.get("output", "/mnt/scratch3/jfoley/gov2.ints"));
    String base = argp.get("input", "/mnt/scratch3/jfoley/gov2.galago");
    DiskNameReader names = new DiskNameReader(base+"/names");
    PositionIndexReader postings = new PositionIndexReader(base+"/postings.krovetz");
    long numDocuments = names.getManifest().getLong("keyCount");
    List<DocumentSplit> documentSplits = DocumentSource.processFile(new File(base+"/corpus"), argp);

    long clen = postings.getManifest().getLong("statistics/collectionLength");
    long universe = 256; // byte[]
    double errCount = 1.0;
    UniversalStringHashFunction hasher = UniversalStringHashFunction.generate(clen, universe, errCount, new Random());

    Debouncer msg = new Debouncer(3000);

    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();
    KrovetzStemmer stemmer = new KrovetzStemmer();

    StreamingStats parsingTime = new StreamingStats();
    StreamingStats tokenizationTime = new StreamingStats();
    StreamingStats processTime = new StreamingStats();
    int skipped = 0;
    try (HashIntCorpusWriter writer = new HashIntCorpusWriter(output, hasher)) {
      for (DocumentSplit documentSplit : documentSplits) {
        DocumentStreamParser parser = DocumentStreamParser.create(documentSplit, argp);

        long st, et;
        while (true) {
          st = System.nanoTime();
          Document doc = parser.nextDocument();
          if (doc == null) break;
          et = System.nanoTime();
          parsingTime.push((et - st) / 1e9);

          st = System.nanoTime();
          tokenizer.tokenize(doc);
          et = System.nanoTime();
          tokenizationTime.push((et -st) / 1e9);

          if(msg.ready()) {
            System.out.println(msg.estimate(writer.nextDocId, numDocuments));
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
    System.out.println("\t parse     : "+parsingTime);
    System.out.println("\t tokenizing: "+tokenizationTime);
    System.out.println("\t processing : "+processTime);
  }

  public static class DirectlyBuildVocabPart {
    public static void main(String[] args) throws Exception {
      Parameters argp = Arguments.parse(args);
      Directory output = new Directory(argp.get("output", "/mnt/scratch3/jfoley/gov2.ints"));
      String base = argp.get("input", "/mnt/scratch3/jfoley/gov2.galago");
      PositionIndexReader postings = new PositionIndexReader(base+"/postings.krovetz");
      long numTerms = postings.getManifest().getLong("keyCount");

      final TIntIntHashMap collisions = new TIntIntHashMap();
      //HashMap<Integer, List<String>> revHash = new HashMap<>();
      Debouncer fst = new Debouncer();
      postings.getIterator().forAllKeyStrings((term) -> {
        int hash = term.hashCode();
        collisions.adjustOrPutValue(hash, 1, 1);
        if(fst.ready()) {
          System.err.println(fst.estimate(collisions.size(), numTerms));
        }
      });

      int hsize = collisions.size();
      System.err.println("Count hashes done.");

      TIntHashSet collides = new TIntHashSet();
      collisions.forEachEntry((hash, count) -> {
        if(count > 1) {
          collides.add(hash);
        }
        return true;
      });
      collisions.clear();
      System.err.println("Count collisions done: "+collides.size()+"/"+hsize);

      Map<Integer, List<String>> revHash = new HashMap<>();
      try(IOMapWriter<Integer, String> writer = GalagoIO.getIOMapWriter(output, "hashVocab", FixedSize.ints, CharsetCoders.utf8).getSorting()) {
        Debouncer snd = new Debouncer();
        postings.getIterator().forAllKeyStrings((term) -> {
          int hash = term.hashCode();
          if (collides.contains(hash)) {
            revHash.computeIfAbsent(hash, missing -> new ArrayList<>()).add(term);
          } else {
            try {
              writer.put(hash, term);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
          if(snd.ready()) {
            System.err.println(snd.estimate(revHash.size(), collides.size()));
          }
        });
        System.err.println("Save non-collisions done.");
        writer.flush();
        for (Map.Entry<Integer, List<String>> kv : revHash.entrySet()) {
          writer.put(kv.getKey(), StrUtil.join(kv.getValue(), "|"));
        }
        System.err.println("Save collisions done.");
      }
    }
    // done.
  }
}

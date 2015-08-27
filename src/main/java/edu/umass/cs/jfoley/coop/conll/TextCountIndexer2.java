package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.TemporaryDirectory;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.ByteBuilder;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.sys.PostingIndex;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.DocumentSource;
import org.lemurproject.galago.core.parse.DocumentStreamParser;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class TextCountIndexer2 {
  public static class CountMetadata implements PostingIndex.KeyMetadata<Integer, CountMetadata> {
    public int totalDocs = 0;
    public int maxCount = 0;
    public int totalCount = 0;

    @Override
    public int totalDocuments() {
      return totalDocs;
    }

    @Override
    public void accumulate(CountMetadata o) {
      this.totalDocs += o.totalDocs;
      this.totalCount += o.totalCount;
      this.maxCount = Math.max(this.maxCount, o.maxCount);
    }

    @Override
    public void accumulate(int document, Integer item) {
      totalDocs++;
      totalCount += item;
      maxCount = Math.max(maxCount, item);
    }

    @Override
    public CountMetadata zero() {
      return new CountMetadata();
    }
  }

  public static class CountMetadataCoder extends Coder<CountMetadata> {
    @Override
    public boolean knowsOwnSize() {
      return true;
    }

    @Nonnull
    @Override
    public DataChunk writeImpl(CountMetadata m) throws IOException {
      ByteBuilder bb = new ByteBuilder();
      bb.add(FixedSize.ints, m.totalDocs);
      bb.add(FixedSize.ints, m.maxCount);
      bb.add(FixedSize.ints, m.totalCount);
      return bb;
    }

    @Nonnull
    @Override
    public CountMetadata readImpl(InputStream inputStream) throws IOException {
      CountMetadata m = new CountMetadata();
      m.totalDocs = FixedSize.ints.readImpl(inputStream);
      m.maxCount = FixedSize.ints.readImpl(inputStream);
      m.totalCount = FixedSize.ints.readImpl(inputStream);
      return m;
    }
  }

  public static void main(String[] args) throws IOException {
    System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "10");

    Directory output = new Directory("test_speed.index");
    Debouncer msg = new Debouncer(1000);

    PostingIndex.PostingsConfig<String,CountMetadata, Integer> countsConfig = new PostingIndex.PostingsConfig<String,CountMetadata,Integer>(

        CharsetCoders.utf8,
        new CountMetadataCoder(),
        VarUInt.instance,
        Comparator.<String>naturalOrder(),
        new CountMetadata()
    );
    long startTime = System.currentTimeMillis();
    PostingIndex.BlockedPostingsWriter<String, CountMetadata, Integer> finalWriter = new PostingIndex.BlockedPostingsWriter<String, CountMetadata, Integer>(countsConfig, output, "segment-based-counts");
    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (PostingIndex.TmpStreamPostingIndexWriter<String, CountMetadata, Integer> writer = new PostingIndex.TmpStreamPostingIndexWriter<>(tmpdir, "counts", countsConfig)) {
        Parameters argp = Parameters.create();
        TagTokenizer tok = new TagTokenizer();
        int i = 0;
        final int TOTAL_DOCS = 1000000;
        for (DocumentSplit split : DocumentSource.processFile(new File("/mnt/scratch/jfoley/robust04raw/"), argp)) {
          try (DocumentStreamParser p = DocumentStreamParser.create(split, argp)) {
            while (true) {
              Document doc = p.nextDocument();
              if (doc == null) break;
              if (msg.ready()) {
                System.err.println("# " + doc.name + " " + i);
                System.err.println("# " + msg.estimate(i, TOTAL_DOCS));
              }

              tok.tokenize(doc);
              HashMap<String, Integer> counts = new HashMap<>();
              for (String term : doc.terms) {
                MapFns.addOrIncrement(counts, term, 1);
              }
              for (Map.Entry<String, Integer> kv : counts.entrySet()) {
                writer.add(kv.getKey(), i, kv.getValue());
              }
              i++;
              if(i > TOTAL_DOCS) break;
            }
          }
          if(i > TOTAL_DOCS) break;
        }
        long endParsingTime = System.currentTimeMillis();
        System.out.println("Total parsing time: "+(endParsingTime - startTime)+"ms.");
        writer.mergeTo(finalWriter);
      }

    }
    long endTime = System.currentTimeMillis();
    System.out.println("Total time: "+(endTime - startTime)+"ms.");
    // 27.6s i>=50
    // 17.2s i>=50 with threaded Sorter
    // parse: 189,280ms, total: 239,167ms i>=500

  }
}

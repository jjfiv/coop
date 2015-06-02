package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.archive.ZipWriter;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.StreamingPostingBuilder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.postings.positions.SimplePositionsList;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.tokenize.Tokenizer;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley.
 */
public class CoIndex {

  public static class Builder implements Closeable, Flushable {
    private final Directory outputDir;
    private final ZipWriter rawCorpusWriter;
    private final Tokenizer tokenizer;
    private final StreamingPostingBuilder<String,PositionsList> positionsBuilder;
    private final StreamingPostingBuilder<String,Integer> lengthWriter;
    private final IOMapWriter<String, Integer> namesWriter;
    private final IOMapWriter<Integer, String> namesRevWriter;
    private int documentId = 0;

    public Builder(Directory outputDir) throws IOException {
      this.outputDir = outputDir;
      this.rawCorpusWriter = new ZipWriter(outputDir.childPath("raw.zip"));
      this.tokenizer = new TagTokenizer();
      this.positionsBuilder = new StreamingPostingBuilder<>(
          CharsetCoders.utf8Raw,
          new PositionsListCoder(),
          GalagoIO.getRawIOMapWriter(outputDir.childPath("positions")));
      this.lengthWriter = new StreamingPostingBuilder<>(
          CharsetCoders.utf8Raw,
          VarUInt.instance,
          GalagoIO.getRawIOMapWriter(outputDir.childPath("lengths"))
          );
      this.namesWriter = GalagoIO.getIOMapWriter(
          CharsetCoders.utf8Raw,
          VarUInt.instance,
          outputDir.childPath("names")
      ).getSorting();
      this.namesRevWriter = GalagoIO.getIOMapWriter(
          VarUInt.instance,
          CharsetCoders.utf8Raw,
          outputDir.childPath("names.reverse")
      ).getSorting();
    }

    public void addDocument(String name, String text) throws IOException {
      System.out.println("+doc:\t"+name+"\t"+ StrUtil.preview(text, 60));
      List<String> terms = tokenizer.tokenize(text).terms;
      int currentId = documentId++;

      // corpus
      rawCorpusWriter.writeUTF8(name, text);
      // write length to flat lengths file.
      lengthWriter.add("doc", currentId, terms.size());
      System.out.println(Pair.of(currentId, name));
      namesWriter.put(name, currentId);
      namesRevWriter.put(currentId, name);

      // collection position vectors:
      Map<String, IntList> data = new HashMap<>();
      for (int i = 0; i < terms.size(); i++) {
        MapFns.extendCollectionInMap(data, terms.get(i), i, new IntList());
      }
      // Add position vectors to builder:
      for (Map.Entry<String, IntList> kv : data.entrySet()) {
        this.positionsBuilder.add(
            kv.getKey(),
            currentId,
            new SimplePositionsList(kv.getValue()));
      }
    }

    @Override
    public void flush() throws IOException {
      positionsBuilder.flush();
      namesWriter.flush();
      namesRevWriter.flush();
      lengthWriter.flush();
    }

    @Override
    public void close() throws IOException {
      rawCorpusWriter.close();
      namesWriter.close();
      namesRevWriter.close();
      lengthWriter.close();
      positionsBuilder.close();
    }
  }
}

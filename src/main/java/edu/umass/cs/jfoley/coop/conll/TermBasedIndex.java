package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.files.RunReader;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.CoopToken;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class TermBasedIndex {
  public static class SentenceIndexedToken implements Comparable<SentenceIndexedToken> {
    public int sentenceId;
    public int tokenId;
    public Map<String,String> terms;
    public Set<String> indicators;

    public SentenceIndexedToken() {
      sentenceId = -1;
      tokenId = -1;
      terms = new HashMap<>();
      indicators = new HashSet<>();
    }
    public SentenceIndexedToken(int sentenceId, int tokenId) {
      this();
      this.sentenceId = sentenceId;
      this.tokenId = tokenId;
    }

    @Override
    public int compareTo(@Nonnull SentenceIndexedToken o) {
      return Integer.compare(tokenId, o.tokenId);
    }
  }

  public static void main(String[] args) throws IOException {
    Directory here = Directory.Read(".");
    for (File file : here.children()) {
      if(file.getName().endsWith(".run")) {
        System.out.println(file);
        List<CoopDoc> collection = new ArrayList<>();
        try (RunReader<CoopDoc> reader = new RunReader<>(new KryoCoder<>(CoopDoc.class), file)) {
          long ms = Timing.milliseconds(() -> {
            IterableFns.intoSink(reader, collection::add);
          });
          System.out.println("Read "+reader.getCount()+" entries in "+ms+ "ms");
        }

        Directory output = here.childDir(file.getName()+".stoken.index");
        long indexBuildTime = Timing.milliseconds(() -> {
          try (TermBasedIndexWriter writer = new TermBasedIndexWriter(output)) {
            for (CoopDoc coopDoc : collection) {
              List<CoopToken> tokens = coopDoc.tokens();
              if(coopDoc.getTags().isEmpty()) continue;
              for (Span stag : coopDoc.getTags().get("true_sentence")) {
                writer.addSentence(ListFns.slice(tokens, stag.begin, stag.end));
              }
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });

        System.out.println("Index build time: "+indexBuildTime);
      }
    }
  }
}

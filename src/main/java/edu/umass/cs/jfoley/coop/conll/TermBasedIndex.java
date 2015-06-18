package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.files.RunReader;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.format.BlockedPostingsCoder;
import edu.umass.cs.ciir.waltz.io.postings.streaming.StreamingPostingBuilder;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.CoopToken;
import edu.umass.cs.jfoley.coop.index.general.DocumentSetWriter;
import edu.umass.cs.jfoley.coop.index.general.NamespacedLabel;
import gnu.trove.map.hash.TObjectIntHashMap;

import javax.annotation.Nonnull;
import java.io.Closeable;
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

  public static class TermBasedIndexReader implements Closeable {
    public final Directory input;
    final IOMap<Integer, List<Integer>> sentenceToTokens;
    final IOMap<Integer, Integer> tokenToSentence;
    final IOMap<Integer, List<SentenceIndexedToken>> sentenceCorpus;
    final IOMap<Integer, SentenceIndexedToken> tokenCorpus;
    /** Inverted index for features */
    final IOMap<String, List<Integer>> featureIndex;
    final IOMap<NamespacedLabel, List<Integer>> tokensByTerms;
    final IOMap<NamespacedLabel, PostingMover<Integer>> sentencesByTerms;

    public TermBasedIndexReader(Directory input) throws IOException {
      this.input = input;
      sentenceToTokens = GalagoIO.openIOMap(
          VarUInt.instance, new DeltaIntListCoder(),
          input.childPath("sentenceToTokens")
      );
      tokenToSentence = GalagoIO.openIOMap(
          VarUInt.instance, VarUInt.instance,
          input.childPath("tokenToSentence")
      );
      Coder<SentenceIndexedToken> tokenCoder = new KryoCoder<>(SentenceIndexedToken.class);
      sentenceCorpus = GalagoIO.openIOMap(
          VarUInt.instance, new ListCoder<>(tokenCoder),
          input.childPath("sentenceCorpus")
      );
      tokenCorpus = GalagoIO.openIOMap(
          VarUInt.instance, tokenCoder,
          input.childPath("tokenCorpus")
      );
      featureIndex = GalagoIO.openIOMap(
          CharsetCoders.utf8, new DeltaIntListCoder(),
          input.childPath("featureIndex")
      );
      tokensByTerms = GalagoIO.openIOMap(
          NamespacedLabel.coder, new DeltaIntListCoder(),
          input.childPath("tokensByTerms")
      );
      sentencesByTerms = GalagoIO.openIOMap(
          NamespacedLabel.coder,
          new BlockedPostingsCoder<Integer>(VarUInt.instance),
          input.childPath("sentencesByTerms")
      );
    }

    @Override
    public void close() throws IOException {
      sentenceToTokens.close();
      tokenToSentence.close();
      sentenceCorpus.close();
      tokenCorpus.close();
      featureIndex.close();
      tokensByTerms.close();
      sentencesByTerms.close();
    }
  }

  public static class TermBasedIndexWriter implements Closeable {
    public final Directory output;
    private int sentenceIndex;
    private int tokenIndex;

    final IOMapWriter<Integer, List<Integer>> sentenceToTokens;
    final IOMapWriter<Integer, Integer> tokenToSentence;
    final IOMapWriter<Integer, List<SentenceIndexedToken>> sentenceCorpus;
    final IOMapWriter<Integer, SentenceIndexedToken> tokenCorpus;
    /** Inverted index for features */
    final DocumentSetWriter<String> featureIndex;
    final DocumentSetWriter<NamespacedLabel> tokensByTerms;
    final StreamingPostingBuilder<NamespacedLabel, Integer> sentencesByTerms;

    public TermBasedIndexWriter(Directory output) throws IOException {
      this.output = output;
      this.sentenceIndex = 0;
      this.tokenIndex = 0;

      sentenceToTokens = GalagoIO.getIOMapWriter(
          VarUInt.instance, new DeltaIntListCoder(),
          output.childPath("sentenceToTokens")
      );
      tokenToSentence = GalagoIO.getIOMapWriter(
          VarUInt.instance, VarUInt.instance,
          output.childPath("tokenToSentence")
      );
      Coder<SentenceIndexedToken> tokenCoder = new KryoCoder<>(SentenceIndexedToken.class);
      sentenceCorpus = GalagoIO.getIOMapWriter(
          VarUInt.instance, new ListCoder<>(tokenCoder),
          output.childPath("sentenceCorpus")
      );
      tokenCorpus = GalagoIO.getIOMapWriter(
          VarUInt.instance, tokenCoder,
          output.childPath("tokenCorpus")
      );
      featureIndex = new DocumentSetWriter<>(
          GalagoIO.getIOMapWriter(
              CharsetCoders.utf8, new DeltaIntListCoder(),
              output.childPath("featureIndex")
          )
      );
      tokensByTerms = new DocumentSetWriter<>(
          GalagoIO.getIOMapWriter(
              NamespacedLabel.coder, new DeltaIntListCoder(),
              output.childPath("tokensByTerms")
          )
      );
      sentencesByTerms = new StreamingPostingBuilder<>(
          NamespacedLabel.coder, VarUInt.instance,
          GalagoIO.getRawIOMapWriter(output.childPath("sentencesByTerms"))
      );
    }

    private void addSentence(List<CoopToken> sentence) throws IOException {

      List<SentenceIndexedToken> tokens = new ArrayList<>();

      int sentenceId = sentenceIndex++;
      IntList tokenIds = new IntList();
      TObjectIntHashMap<NamespacedLabel> ttf = new TObjectIntHashMap<>();
      for (CoopToken coopToken : sentence) {
        int tokenId = tokenIndex++;
        SentenceIndexedToken token = new SentenceIndexedToken(sentenceId, tokenId);
        tokens.add(token);
        tokenIds.add(tokenId);
        tokenCorpus.put(tokenId, token);

        for (String feature : coopToken.getIndicators()) {
          featureIndex.process(feature, tokenId);
        }
        for (Map.Entry<String, String> kv : coopToken.getTerms().entrySet()) {
          NamespacedLabel termTypeAndTerm = new NamespacedLabel(kv.getKey(), kv.getValue());
          tokensByTerms.process(termTypeAndTerm, tokenId);
          ttf.adjustOrPutValue(termTypeAndTerm, 1, 1);
        }

        tokenToSentence.put(tokenId, sentenceIndex);
      }

      ttf.forEachEntry((k, count) -> {
        sentencesByTerms.add(k, sentenceId, count);
        return true;
      });

      sentenceToTokens.put(sentenceId, tokenIds);
      sentenceCorpus.put(sentenceId, tokens);
    }

    @Override
    public void close() throws IOException {
      sentenceToTokens.close();
      tokenToSentence.close();
      sentenceCorpus.close();
      tokenCorpus.close();
      featureIndex.close();
      tokensByTerms.close();
      sentencesByTerms.close();
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

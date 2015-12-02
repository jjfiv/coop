package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.bills.IntVocabBuilder;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.util.List;

/**
 * @author jfoley
 */
public class TaggerStats {
  public static void main(String[] args) throws IOException {
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read("dbpedia.ints"));

    int N = 10;
    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();

    IdMaps.IdReader<String> names = dbpedia.getNames();
    System.err.println("Total: " + names.size());
    int count = IntMath.fromLong(names.size());
    Debouncer msg = new Debouncer(500);
    PhraseDetector detector = new PhraseDetector(N);

    long start = System.currentTimeMillis();
    TObjectIntHashMap<String> vocabLookup = new TObjectIntHashMap<>(IntMath.fromLong(dbpedia.getTermVocabulary().size()));
    for (Pair<Integer, String> kv : dbpedia.getTermVocabulary().items()) {
      vocabLookup.put(StrUtil.collapseSpecialMarks(kv.getValue()), kv.getKey());
    }
    long end = System.currentTimeMillis();
    System.err.println("# preload vocab: "+(end-start)+"ms.");

    IntVocabBuilder.IntVocabReader corpus = dbpedia.getCorpus();

    TIntIntHashMap phraseIdCount = new TIntIntHashMap();

    int docNameIndex = 0;
    int ND = corpus.numberOfDocuments();
    for (Pair<Integer,String> pair : names.items()) {
      int phraseId = pair.left;
      String name = pair.right;


      docNameIndex++;
      String text = IntCoopIndex.parseDBPediaTitle(name);
      List<String> query = tokenizer.tokenize(text).terms;
      int size = query.size();
      if(size == 0 || size > N) continue;
      IntList qIds = new IntList(query.size());
      for (String str : query) {
        int tid = vocabLookup.get(str);
        if(tid == vocabLookup.getNoEntryValue()) {
          qIds = null;
          break;
        }
        qIds.push(tid);
      }
      // vocab mismatch; phrase-match therefore not possible
      if(qIds == null) continue;

      int pattern = detector.addPattern(qIds, phraseId);
      if(pattern != -1) {
        phraseIdCount.adjustOrPutValue(pattern, 1, 1);
      }

      assert(pair.left < ND);

      if(msg.ready()) {
        System.err.println(text);
        //System.err.println(getDocument(phraseId));
        System.err.println(query);
        System.err.println(qIds);
        System.err.println(msg.estimate(docNameIndex, count));
        System.err.println(detector);
      }
    }

    StreamingStats stats = new StreamingStats();
    phraseIdCount.forEachEntry((id, freq) -> {
      if(freq == 408) {
        try {
          System.err.println("Maximum: "+names.getForward(id));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      stats.push(freq);
      return true;
    });

    System.err.println(stats);
  }
}

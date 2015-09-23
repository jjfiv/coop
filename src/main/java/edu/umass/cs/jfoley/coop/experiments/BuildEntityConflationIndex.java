package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.IntListCoder;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static edu.umass.cs.jfoley.coop.bills.IntCoopIndex.parseDBPediaTitle;

/**
 * @author jfoley
 */
public class BuildEntityConflationIndex {
  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("robust.ints"));
    IntCoopIndex index = new IntCoopIndex(new Directory("dbpedia.ints"));

    int N = 20;

    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();
    System.err.println("Total: " + index.getNames().size());
    int count = IntMath.fromLong(index.getNames().size());
    PhraseDetector detector = new PhraseDetector(N);

    HashMap<Integer, HashSet<Integer>> ambiguityIndex = new HashMap<>();
    long start = System.currentTimeMillis();
    TObjectIntHashMap<String> vocabLookup = new TObjectIntHashMap<>(IntMath.fromLong(target.getTermVocabulary().size()));
    for (Pair<Integer, String> kv : target.getTermVocabulary().items()) {
      vocabLookup.put(StrUtil.collapseSpecialMarks(kv.getValue()), kv.getKey());
    }
    long end = System.currentTimeMillis();
    System.err.println("# preload vocab: "+(end-start)+"ms.");
    int docNameIndex = 0;
    int ND = index.getCorpus().numberOfDocuments();

    Debouncer msg = new Debouncer(2000);
    for (Pair<Integer,String> pair : index.getNames().items()) {
      int phraseId = pair.left;
      String name = pair.right;


      docNameIndex++;
      String text = parseDBPediaTitle(name);
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

      Integer matches = detector.getMatch(qIds);
      if(matches != null) {
        HashSet<Integer> confusing = ambiguityIndex.computeIfAbsent(phraseId, ignored -> new HashSet<>());
        confusing.add(matches);
        confusing.add(phraseId);
      } else {
        detector.addPattern(qIds, phraseId);
      }

      assert(pair.left < ND);

      if(msg.ready()) {
        System.err.println(text);
        System.err.println(ambiguityIndex.size());
        //System.err.println(getDocument(phraseId));
        System.err.println(query);
        System.err.println(qIds);
        System.err.println(msg.estimate(docNameIndex, count));
        System.err.println(detector);
      }
    }

    try (IOMapWriter<Integer,IntList> writer = GalagoIO.getIOMapWriter(target.baseDir, "dbpedia.ambiguous", FixedSize.ints, IntListCoder.instance).getSorting()) {
      for (Map.Entry<Integer, HashSet<Integer>> kv : ambiguityIndex.entrySet()) {
        int eid = kv.getKey();
        IntList ids = new IntList(kv.getValue());
        writer.put(eid, ids);
      }
    }
    System.out.println(ambiguityIndex.size());
  }
}

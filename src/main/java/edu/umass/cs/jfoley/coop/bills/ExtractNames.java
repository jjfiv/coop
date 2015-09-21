package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsWriter;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.util.List;

/**
 * @author jfoley
 */
public class ExtractNames {
  public static void main(String[] args) throws IOException {
    IntCoopIndex target = new IntCoopIndex(new Directory("robust.ints"));
    IntCoopIndex index = new IntCoopIndex(new Directory("dbpedia.ints"));

    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();

    System.err.println("Total: " + index.names.size());
    int count = IntMath.fromLong(index.names.size());

    Debouncer msg = new Debouncer(500);

    int N = 20;
    PhraseDetector detector = new PhraseDetector(N);

    long start = System.currentTimeMillis();
    TObjectIntHashMap<String> vocabLookup = new TObjectIntHashMap<>(IntMath.fromLong(target.vocab.size()));
    for (Pair<Integer, String> kv : target.vocab.items()) {
      vocabLookup.put(StrUtil.collapseSpecialMarks(kv.getValue()), kv.getKey());
    }
    long end = System.currentTimeMillis();
    System.err.println("# preload vocab: "+(end-start)+"ms.");
    int docNameIndex = 0;
    for (Pair<Integer,String> pair : index.names.items()) {
      int phraseId = pair.left;
      String name = pair.right;

      docNameIndex++;
      // make "el ni&ntilde;o" -> "el nino"
      String text = StrUtil.collapseSpecialMarks(name.replace('_', ' '));
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

      detector.addPattern(qIds, phraseId);

      if(msg.ready()) {
        System.out.println(text);
        System.out.println(query);
        System.out.println(qIds);
        System.out.println(msg.estimate(docNameIndex, count));
        System.out.println(detector);
      }
    }

    System.out.println(detector);

    // Vocabulary loaded:

    // Now, see NERIndex
    ExtractNames234.CorpusTagger tagger = new ExtractNames234.CorpusTagger(detector, target.getCorpus());

    Debouncer msg2 = new Debouncer(2000);
    try (PhraseHitsWriter writer = new PhraseHitsWriter(target.baseDir, "dbpedia")) {
      tagger.tag(msg2, (phraseId, docId, hitStart, hitSize, terms) -> {
        writer.onPhraseHit(phraseId, docId, hitStart, hitSize, IntList.clone(terms, hitStart, hitSize));
      });
    } // phrase-hits-writer
  }
}

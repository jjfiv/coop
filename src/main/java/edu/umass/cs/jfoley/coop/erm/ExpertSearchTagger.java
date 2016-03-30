package edu.umass.cs.jfoley.coop.erm;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.IntListCoder;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.bills.ExtractNames234;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsWriter;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class ExpertSearchTagger {
  public static void main(String[] args) throws IOException {

    IntCoopIndex target = new IntCoopIndex(new Directory("/mnt/scratch3/jfoley/w3c.ints"));
    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();
    int N = 10;
    //PhraseDetector detector = target.loadPhraseDetector(N, target);
    PhraseDetector detector = new PhraseDetector(N);

    // phrase -> mention id
    HashMap<List<Integer>, Integer> mentionId = new HashMap<>();
    // mention id -> entitiy ids
    HashMap<Integer, HashSet<Integer>> ambiguityIndex = new HashMap<>();

    for (String line : LinesIterable.fromFile("/home/jfoley/data/enterprise_track/ent05.expert.candidates").slurp()) {
      String col[] = line.split("\\s+");
      if(col.length < 3) {
        continue;
      }
      List<String> cols = Arrays.asList(col);

      String id = col[0];
      String name = StrUtil.join(ListFns.slice(cols, 1, col.length-1));
      String email = col[col.length-1];
      assert(id.startsWith("candidate-"));
      int numericId = Integer.parseInt(StrUtil.takeAfter(id, "-"));

      // construct name variations heuristically:
      List<String> variations = new ArrayList<>();
      variations.add(email);
      variations.add(name);
      variations.add(StrUtil.collapseSpecialMarks(name));

      HashSet<List<String>> surfaceForms = new HashSet<>();
      HashSet<String> uterms = new HashSet<>();
      for (String variation : variations) {
        List<String> tokens = tokenizer.tokenize(variation).terms;
        surfaceForms.add(tokens);
        uterms.addAll(tokens);
      }

      System.out.println(line.trim());
      System.out.println(numericId+"\t"+name+"\t|\t"+email);
      System.out.println("\t"+surfaceForms);

      Map<String, Integer> relevantVocabulary = target.getTermVocabulary().getReverseMap(new ArrayList<>(uterms));
      for (List<String> surfaceForm : surfaceForms) {
        IntList ids = new IntList();
        boolean okay = true;
        for (String term : surfaceForm) {
          int tid = relevantVocabulary.getOrDefault(term, -1);
          if(tid == -1) {
            okay = false;
            break;
          }
          ids.push(tid);
        }

        if(!okay) { // skip ones with no terms
          continue;
        }

        Integer currentId = mentionId.get(ids);
        if(currentId == null) {
          currentId = mentionId.size()+1; // mention ids from 1...+
          detector.addPattern(ids, currentId); // add pattern pointing to this mention id
          mentionId.put(ids, currentId);
        }
        // add mention -> zip id
        ambiguityIndex.computeIfAbsent(currentId, missing -> new HashSet<>()).add(numericId);
      }

    }

    System.out.println("# Ambiguity Index Size: "+ambiguityIndex.size());

    try (IOMapWriter<Integer,IntList> writer = GalagoIO.getIOMapWriter(target.baseDir, "experts.ambiguous", FixedSize.ints, IntListCoder.instance).getSorting()) {
      // phraseId -> entitiy ids:
      for (Map.Entry<Integer, HashSet<Integer>> sform : ambiguityIndex.entrySet()) {
        writer.put(sform.getKey(), new IntList(sform.getValue()));
      }
    }

    System.out.println("# End writing Ambiguity Index.");
    ambiguityIndex.clear();

    // start tagging:
    // Now, see NERIndex
    ExtractNames234.CorpusTagger tagger = new ExtractNames234.CorpusTagger(detector, target.getCorpus());

    Debouncer msg2 = new Debouncer(2000);
    Directory output = target.baseDir;
    //output = new Directory("test.foo");
    try (PhraseHitsWriter writer = new PhraseHitsWriter(output, "experts")) {
      tagger.tag(msg2, (phraseId, docId, hitStart, hitSize, terms) -> {
        IntList data_found = IntList.clone(terms, hitStart, hitSize);
        writer.onPhraseHit(phraseId, docId, hitStart, hitSize, data_found);
      });
    } // phrase-hits-writer
  }
}

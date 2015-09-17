package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.TemporaryDirectory;
import edu.umass.cs.jfoley.coop.bills.ExtractNames234;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.bills.IntCorpusPositionIndexer;
import edu.umass.cs.jfoley.coop.bills.IntVocabBuilder;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class PhraseHitsReaderTest {

  public static IntCoopIndex toyIndex(Directory target) throws IOException {
    List<String> documents = new ArrayList<>();
    documents.add("this is the time for all good men to come to the party");
    documents.add("the quick brown fox jumped over the lazy dog");
    documents.add("any dog would want to go to a party");

    List<List<String>> docTerms = ListFns.map(documents,
        (doc) -> Arrays.asList(doc.split("\\s+")));

    List<String> corpus = new ArrayList<>();
    HashSet<String> uniqueTerms = new HashSet<>();
    for (List<String> docTerm : docTerms) {
      corpus.addAll(docTerm);
    }

    try (IntVocabBuilder.IntVocabWriter writer = new IntVocabBuilder.IntVocabWriter(target)) {
      for (int i = 0; i < docTerms.size(); i++) {
        List<String> doc = docTerms.get(i);
        writer.process("doc"+i, doc);
      }
    }
    // finish building names parts
    try (IntCoopIndex reader = new IntCoopIndex(target)) { }

    // build a positions part:
    try (IntVocabBuilder.IntVocabReader reader = new IntVocabBuilder.IntVocabReader(target);
         IntCorpusPositionIndexer pwriter = new IntCorpusPositionIndexer(target)) {
      pwriter.indexFromCorpus(reader);
    }

    return new IntCoopIndex(target);
  }

  @Test
  public void testLoadAndRead() throws IOException {
    try (TemporaryDirectory tmpdir = new TemporaryDirectory();
         IntCoopIndex index = toyIndex(tmpdir)) {

      List<List<String>> phrasesToTag = new ArrayList<>();


      Set<List<String>> phrasesThatWillBeFound = new HashSet<>();
      phrasesThatWillBeFound.addAll(Arrays.asList(
          Collections.singletonList("party"),
          Collections.singletonList("dog"),
          Arrays.asList("lazy", "dog"),
          Arrays.asList("all", "good", "men"),
          Arrays.asList("quick", "brown", "fox")
      ));
      phrasesToTag.add(Arrays.asList("i just met a girl named maria".split("\\s+")));
      phrasesToTag.addAll(phrasesThatWillBeFound);

      PhraseDetector det = new PhraseDetector(4);
      for (List<String> phrase : phrasesToTag) {
        det.addPattern(index.translateFromTerms(phrase));
      }

      ExtractNames234.CorpusTagger tagger = new ExtractNames234.CorpusTagger(det, index.getCorpus());

      try (PhraseHitsWriter writer = new PhraseHitsWriter(tmpdir, "foo")) {
        tagger.tag(null, (doc,start,size,terms) -> {
          writer.onPhraseHit(doc, start, size, IntList.clone(terms, start, size));
        });
      }
      try (
          PhraseHitsReader reader = new PhraseHitsReader(index, tmpdir, "foo")) {
        HashSet<List<String>> phrasesFound = new HashSet<>();
        for (Pair<Integer, IntList> pr : reader.vocab.items()) {
          int id = pr.left;
          IntList words = pr.right;
          phrasesFound.add(index.translateToTerms(words));

          System.out.println(reader.postings.get(id).toMap());
        }
        assertEquals(phrasesThatWillBeFound, phrasesFound);

        System.out.println(reader.docHits.get(0));

        System.out.println(reader.toString());
      }

    }
  }

}
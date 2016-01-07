package edu.umass.cs.jfoley.coop.erm;

import au.com.bytecode.opencsv.CSVReader;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
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
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class ZipCodeTagger {

 public static final Map<String, String> STATE_MAP;
  static {
    STATE_MAP = new HashMap<>();
    STATE_MAP.put("AL", "Alabama");
    STATE_MAP.put("AK", "Alaska");
    STATE_MAP.put("AB", "Alberta");
    STATE_MAP.put("AZ", "Arizona");
    STATE_MAP.put("AR", "Arkansas");
    STATE_MAP.put("BC", "British Columbia");
    STATE_MAP.put("CA", "California");
    STATE_MAP.put("CO", "Colorado");
    STATE_MAP.put("CT", "Connecticut");
    STATE_MAP.put("DE", "Delaware");
    STATE_MAP.put("DC", "District Of Columbia");
    STATE_MAP.put("FL", "Florida");
    STATE_MAP.put("GA", "Georgia");
    STATE_MAP.put("GU", "Guam");
    STATE_MAP.put("HI", "Hawaii");
    STATE_MAP.put("ID", "Idaho");
    STATE_MAP.put("IL", "Illinois");
    STATE_MAP.put("IN", "Indiana");
    STATE_MAP.put("IA", "Iowa");
    STATE_MAP.put("KS", "Kansas");
    STATE_MAP.put("KY", "Kentucky");
    STATE_MAP.put("LA", "Louisiana");
    STATE_MAP.put("ME", "Maine");
    STATE_MAP.put("MB", "Manitoba");
    STATE_MAP.put("MD", "Maryland");
    STATE_MAP.put("MA", "Massachusetts");
    STATE_MAP.put("MI", "Michigan");
    STATE_MAP.put("MN", "Minnesota");
    STATE_MAP.put("MS", "Mississippi");
    STATE_MAP.put("MO", "Missouri");
    STATE_MAP.put("MT", "Montana");
    STATE_MAP.put("NE", "Nebraska");
    STATE_MAP.put("NV", "Nevada");
    STATE_MAP.put("NB", "New Brunswick");
    STATE_MAP.put("NH", "New Hampshire");
    STATE_MAP.put("NJ", "New Jersey");
    STATE_MAP.put("NM", "New Mexico");
    STATE_MAP.put("NY", "New York");
    STATE_MAP.put("NF", "Newfoundland");
    STATE_MAP.put("NC", "North Carolina");
    STATE_MAP.put("ND", "North Dakota");
    STATE_MAP.put("NT", "Northwest Territories");
    STATE_MAP.put("NS", "Nova Scotia");
    STATE_MAP.put("NU", "Nunavut");
    STATE_MAP.put("OH", "Ohio");
    STATE_MAP.put("OK", "Oklahoma");
    STATE_MAP.put("ON", "Ontario");
    STATE_MAP.put("OR", "Oregon");
    STATE_MAP.put("PA", "Pennsylvania");
    STATE_MAP.put("PE", "Prince Edward Island");
    STATE_MAP.put("PR", "Puerto Rico");
    STATE_MAP.put("QC", "Quebec");
    STATE_MAP.put("RI", "Rhode Island");
    STATE_MAP.put("SK", "Saskatchewan");
    STATE_MAP.put("SC", "South Carolina");
    STATE_MAP.put("SD", "South Dakota");
    STATE_MAP.put("TN", "Tennessee");
    STATE_MAP.put("TX", "Texas");
    STATE_MAP.put("UT", "Utah");
    STATE_MAP.put("VT", "Vermont");
    STATE_MAP.put("VI", "Virgin Islands");
    STATE_MAP.put("VA", "Virginia");
    STATE_MAP.put("WA", "Washington");
    STATE_MAP.put("WV", "West Virginia");
    STATE_MAP.put("WI", "Wisconsin");
    STATE_MAP.put("WY", "Wyoming");
    STATE_MAP.put("YT", "Yukon Territory");
  }

  public static void main(String[] args) throws IOException {

    IntCoopIndex target = new IntCoopIndex(new Directory("/mnt/scratch3/jfoley/robust.ints"));
    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();
    int N = 10;
    //PhraseDetector detector = target.loadPhraseDetector(N, target);
    PhraseDetector detector = new PhraseDetector(N);

    // phrase -> mention id
    HashMap<List<Integer>, Integer> mentionId = new HashMap<>();
    // mention id -> entitiy ids
    HashMap<Integer, HashSet<Integer>> ambiguityIndex = new HashMap<>();

    String zipcsv = "/home/jfoley/data/zipcodes.csv";
    try (CSVReader reader = new CSVReader(IO.openReader(zipcsv))) {
      String[] header = reader.readNext();
      System.out.println(Arrays.toString(header));
      while(true) {
        String[] row = reader.readNext();
        if(row == null) break;

        Parameters rowP = Parameters.create();
        for (int i = 0; i < row.length; i++) {
          rowP.put(header[i], row[i]);
        }
        int zipId = Integer.parseInt(rowP.getString("zip"));
        if(zipId == 99999) { continue; } // skip the unknown-zip

        // construct name variations heuristically:
        List<String> variations = new ArrayList<>();
        variations.add(rowP.getString("name"));
        String county = rowP.getString("cty_name");
        variations.add(county);
        variations.add(rowP.getString("zip"));
        String city = rowP.getString("city");
        variations.add(city);
        String stateAbbr = rowP.getString("stabbr");
        String state = STATE_MAP.get(stateAbbr);
        variations.add(stateAbbr);
        variations.add(state);
        variations.add(county+" county");
        variations.add(county+" county, "+state);
        variations.add(county+" county, "+stateAbbr);
        variations.add(county+", "+stateAbbr);
        variations.add(city+", "+state);
        variations.add(city+", "+stateAbbr);
        variations.add(city+", "+county);

        HashSet<List<String>> surfaceForms = new HashSet<>();
        HashSet<String> uterms = new HashSet<>();
        for (String variation : variations) {
          List<String> tokens = tokenizer.tokenize(variation).terms;
          surfaceForms.add(tokens);
          uterms.addAll(tokens);
        }

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
          ambiguityIndex.computeIfAbsent(currentId, missing -> new HashSet<Integer>()).add(zipId);
        }
      }
    }

    System.out.println("# Ambiguity Index Size: "+ambiguityIndex.size());

    try (IOMapWriter<Integer,IntList> writer = GalagoIO.getIOMapWriter(target.baseDir, "zipcode.pambiguous", FixedSize.ints, IntListCoder.instance).getSorting()) {
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
    try (PhraseHitsWriter writer = new PhraseHitsWriter(output, "zipcode")) {
      tagger.tag(msg2, (phraseId, docId, hitStart, hitSize, terms) -> {
        IntList data_found = IntList.clone(terms, hitStart, hitSize);
        writer.onPhraseHit(phraseId, docId, hitStart, hitSize, data_found);
      });
    } // phrase-hits-writer
  }
}

package edu.umass.cs.jfoley.coop.pagerank;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.time.Debouncer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * @author jfoley
 */
public class NameMapping {
  public List<String> sortedListOfNames;
  NameMapping(Set<String> uniqueNames) {
    sortedListOfNames = new ArrayList<>(uniqueNames.size());
    sortedListOfNames.addAll(uniqueNames);
    Collections.sort(sortedListOfNames);
  }

  /** load from reader, for testing */
  public static NameMapping load(Reader input) throws IOException {
    HashSet<String> uniqueNames = new HashSet<>();

    Debouncer msg = new Debouncer();
    int i=0;
    try (BufferedReader reader = new BufferedReader(input)) {

      String lastSrc = null;
      while(true) {
        i++;
        // every 10,000 print progress.
        if(PageRank.printProgress && msg.ready()) {
          System.err.printf("Processed %d lines, %d unique links.\n", i, uniqueNames.size());
        }
        String line = reader.readLine();
        if(line == null) break;

        int split = line.indexOf('\t');
        String src = line.substring(0, split);
        String target = line.substring(split+1);

        if(!src.equals(lastSrc)) {
          uniqueNames.add(src);
          lastSrc = src;
        }
        uniqueNames.add(target);
      }
    }

    return new NameMapping(uniqueNames);
  }

  /** load from file */
  public static NameMapping load(String srtFile) throws IOException {
    return load(IO.openReader(srtFile));
  }

  /** lookup name of node with id=index */
  public String getName(int index) {
    return sortedListOfNames.get(index);
  }
  /** lookup index of node with given name 'query' */
  public int findIdForName(String query) {
    return Collections.binarySearch(sortedListOfNames, query);
  }

  /** The size of the unique name set |P| */
  public int size() {
    return sortedListOfNames.size();
  }
}

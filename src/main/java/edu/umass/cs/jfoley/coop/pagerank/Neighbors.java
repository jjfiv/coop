package edu.umass.cs.jfoley.coop.pagerank;

import ciir.jfoley.chai.io.IO;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * @author jfoley
 */
public class Neighbors {
  private final TIntObjectHashMap<int[]> neighbors;
  private static final int[] EmptyArr = new int[0];

  public Neighbors(TIntObjectHashMap<int[]> neighborsByIndex) {
    this.neighbors = neighborsByIndex;
  }

  public int[] getOutlinks(int index) {
    int[] arr = neighbors.get(index);
    // If we look for outlinks of something that doesn't have any, we should return an "empty set".
    if(arr == null) return EmptyArr;
    return arr;
  }

  public static Neighbors load(String inputFile, NameMapping names) throws IOException {
    return load(IO.openReader(inputFile), names);
  }
  public static Neighbors load(Reader input, NameMapping names) throws IOException {
    TIntObjectHashMap<int[]> neighbors = new TIntObjectHashMap<>();

    TIntArrayList currentNeighborList = new TIntArrayList();
    try (BufferedReader reader = new BufferedReader(input)) {
      int i=0;
      String lastSrc = null;
      int lastSrcIndex = -1;
      while(true) {
        i++;
        // every 10,000 print progress.
        if(PageRank.printProgress && i % 10000 == 0) {
          System.err.printf("Processed %d lines, %d with neighbors.\n", i, neighbors.size());
        }

        // Read a line and break on EOF.
        String line = reader.readLine();
        if(line == null) break;

        // Parse the file.
        int split = line.indexOf('\t');
        String src = line.substring(0, split);
        String target = line.substring(split+1);

        // Group by src.
        if(!src.equals(lastSrc)) {
          if(lastSrc != null) {
            // If we just hit a new source, push the previous one's data.
            neighbors.put(lastSrcIndex, currentNeighborList.toArray());
          }

          // reset the list we're building for the new source:
          currentNeighborList.clear();
          lastSrc = src;
          lastSrcIndex = names.findIdForName(lastSrc);
        }

        // Add the current target to the list we're building.
        int neighborId = names.findIdForName(target);
        currentNeighborList.add(neighborId);
      }
      // Push the data of the last source we encountered in the list.
      if(lastSrc != null) {
        neighbors.put(lastSrcIndex, currentNeighborList.toArray());
      }
    }
    return new Neighbors(neighbors);
  }
}

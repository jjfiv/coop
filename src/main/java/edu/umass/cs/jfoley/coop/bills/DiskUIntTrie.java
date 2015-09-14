package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.util.*;

/**
 * @author jfoley
 */
public class DiskUIntTrie {
  /** Re-use root node, since a loop back to the beginning would be degenerate */
  public static final int NO_MATCH = 0;

  ArrayList<TrieNode> nodes;

  public DiskUIntTrie() {
    nodes = new ArrayList<>();
    nodes.add(new TrieNode(1));
  }

  public TrieNode root() {
    return nodes.get(0);
  }

  public static class TrieNode {
    int myIndex;
    TIntIntHashMap keyMapping;
    TIntHashSet doneMapping;

    public TrieNode(int myIndex) {
      this.myIndex = myIndex;
      keyMapping = new TIntIntHashMap();
      doneMapping = new TIntHashSet();
    }

    public int find(int i) {
      if(keyMapping.containsKey(i)) {
        return keyMapping.get(i);
      }
      return NO_MATCH;
    }

    public void insert(int i, TrieNode rest) {
      assert(!keyMapping.containsKey(i));
      keyMapping.put(i, rest.myIndex);
    }

    public void markLast(int i) {
      doneMapping.add(i);
    }


    @Override
    public String toString() {
      return "TrieNode["+myIndex+"]={keys="+keyMapping+", done={"+doneMapping+"}";
    }
  }

  private TrieNode allocate() {
    int id = this.nodes.size();
    TrieNode newNode = new TrieNode(id);
    this.nodes.add(newNode);
    return newNode;
  }

  public static class IntListCmp implements Comparator<IntList> {
    @Override
    public int compare(IntList o1, IntList o2) {
      int size = Math.min(o1.size(), o2.size());
      for (int i = 0; i < size; i++) {
        int cmp = Integer.compare(o1.getQuick(i), o2.getQuick(i));
        if(cmp != 0) return cmp;
      }
      return 0;
    }
  }
  private void insert(IntList aData) {
    insert(nodes.get(0), aData.unsafeArray(), 0, aData.size());
  }

  private void insert(TrieNode root, int[] data, int index, int size) {
    // nothing to insert:
    if(index >= size) return;

    // this is the last character:
    if(index+1 == size) {
      root.markLast(data[index]);
      return;
    }

    // this is an intermediate character:
    int pos = root.find(data[index]);
    TrieNode rest;
    if(pos == NO_MATCH) {
      rest = allocate();
      root.insert(data[index], rest);
    } else {
      rest = nodes.get(pos);
    }
    insert(rest, data, index+1, size);
  }

  public static void main(String[] args) {
    List<IntList> data = Arrays.asList(
        new IntList(Arrays.asList(1,2,3,4)),
        new IntList(Arrays.asList(1,2,3)),
        new IntList(Arrays.asList(3,2,3)),
        new IntList(Arrays.asList(3,2)),
        new IntList(Arrays.asList(3)),
        new IntList(Arrays.asList(2)),
        new IntList(Arrays.asList(1))
    );

    Collections.sort(data, new IntListCmp());

    System.out.println(data);

    DiskUIntTrie trie = new DiskUIntTrie();
    for (IntList aData : data) {
      trie.insert(aData);
      System.out.println(trie.nodes);
    }
  }

}

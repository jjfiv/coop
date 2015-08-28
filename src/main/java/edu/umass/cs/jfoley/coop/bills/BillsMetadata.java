package edu.umass.cs.jfoley.coop.bills;

import au.com.bytecode.opencsv.CSVReader;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.IO;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.json.JSONUtil;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class BillsMetadata {
  public enum BillsVariableKind {
    BILL,
    SPONSOR,
  }

  public static class BillsVariableDef {
    public BillsVariableKind kind; // "Class";
    public String name;
    public String type;
    public boolean nullable;
    public String description;
    public String source;
    public String note;

    @Override
    public String toString() {
      return "<"+name+":"+type+(nullable ? "?":"!")+">";
    }
  }

  public static void main(String[] args) throws IOException {
    List<BillsVariableDef> vars = new ArrayList<>();
    try (CSVReader reader = new CSVReader(IO.openReader("bills-data/Variable_Codebook.csv"))) {
      String[] header = reader.readNext();
      System.out.println(Arrays.toString(header));
      while(true) {
        String[] row = reader.readNext();
        if(row == null) break;
        BillsVariableDef vdef = new BillsVariableDef();
        System.out.println(Arrays.toString(row));
        vdef.kind = row[0].equalsIgnoreCase("bill") ? BillsVariableKind.BILL : BillsVariableKind.SPONSOR;
        vdef.name = row[1];
        vdef.type = row[2];
        vdef.nullable = row[3].equalsIgnoreCase("yes");
        vdef.description = row[4];
        vdef.source = row[5];
        vdef.note = row[6];
        vars.add(vdef);
      }
    }

    HashMap<String,BillsVariableDef> varsByName = new HashMap<>();
    HashMap<String,Integer> types = new HashMap<>();
    for (BillsVariableDef var : vars) {
      MapFns.addOrIncrement(types, var.type, 1);
      varsByName.put(var.name, var);
    }

    System.out.println(types);

    try (CSVReader reader = new CSVReader(IO.openReader("bills-data/Reduced_Bill_Metadata_1993-2014.csv"))) {
      String[] header = reader.readNext();
      System.out.println(Arrays.toString(header));
      List<BillsVariableDef> varByColumn = new ArrayList<>();
      for (String headerStr : header) {
        varByColumn.add(varsByName.get(headerStr));
      }
      //while(true)
      {
        Parameters billInfo = Parameters.create();

        String[] row = reader.readNext();
        //if (row == null) break;
        for (int i = 0; i < row.length; i++) {
          String name = header[i];
          if(name.equals("time")) continue;

          BillsVariableDef vdef = varByColumn.get(i);
          String value = row[i];

          Object specialValue = null;
          if(!Objects.equals(value, "NA") && value != null) {
            if(vdef == null) {
              specialValue = JSONUtil.parseString(value);
            } else {
              if(vdef.nullable) {
                if(value.equalsIgnoreCase("null") || value.isEmpty()) {
                  value = null;
                }
              }
              if(value != null) {
                switch (vdef.type) {
                  case "Unsigned integer":
                    specialValue = Integer.parseInt(value);
                    break;
                  case "Boolean":
                    specialValue = value.equalsIgnoreCase("1");
                    break;
                  case "Text":
                  case "Date":
                    specialValue = value;
                    break;
                  case "Floating point":
                    specialValue = Double.parseDouble(value);
                    break;
                  // ignored for now:
                  case "Array of booleans":
                    break;
                  default:
                    throw new RuntimeException(vdef.toString());
                }
              }
            }
          }

          if(specialValue != null) {
            billInfo.put(name, specialValue);
          }
        }
        System.out.println(billInfo);


      }
    }


  }
}

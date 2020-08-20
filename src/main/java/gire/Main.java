package gire;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.util.Pair;
import org.neo4j.driver.Result;

public class Main {
  public static Map<String, String> prefixes = new HashMap<String, String>();
  public static Map<String, String[]> rules = new HashMap<String, String[]>();
  public static List<String[]> conjectures = new ArrayList<String[]>();

  public static void main(String[] args) {
    Boolean overwrite = false;

    Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "password"));
    Session session = driver.session();

    FileManager.get().addLocatorClassLoader(Main.class.getClassLoader());
    Model model = FileManager.get().loadModel("wine.rdf");

    StmtIterator iter = model.listStatements();

    if (overwrite) {
      session.run("MATCH (n) DETACH DELETE n");
      while (iter.hasNext()) {
        Statement stmt = iter.next();
        Property predicate = stmt.getPredicate();
        Resource subject = stmt.getSubject();
        RDFNode object = stmt.getObject();

        String subjectURI = subject.getURI();
        String objectURI = null;

        try {
          objectURI = object.asResource().getURI();
        } catch (Exception e) {
        }

        if (subjectURI == null || subjectURI.length() == 0 || subjectURI.equals("null"))
          subjectURI = subject.toString();

        if (objectURI == null || objectURI.length() == 0 || objectURI.equals("null"))
          objectURI = object.toString();

        if (predicate.getLocalName().equals("type")) {
          session
              .run("MERGE (subject {uri:\"" + subjectURI + "\"}) SET subject :" + object.asResource().getLocalName());
        } else {
          session.run("MERGE (subject {uri:\"" + subjectURI + "\"})");
          session.run("MERGE (object {uri:\"" + objectURI + "\"})");
          session.run("MATCH (subject {uri:\"" + subjectURI + "\"}), (object {uri:\"" + objectURI + "\"}) "
              + "MERGE((subject)-[:" + predicate.getLocalName() + " {uri: \"" + predicate.getURI()
              + "\"}]-> (object));");
        }

      }
    }

    try {
      File myObj = new File("example.txt");
      Scanner myReader = new Scanner(myObj);
      while (myReader.hasNextLine()) {
        String l = myReader.nextLine();
        int idStart = l.indexOf("@");
        int idEnd = l.indexOf(" ");
        String myCase = "";
        if (idEnd > idStart)
          myCase = l.substring(idStart + 1, idEnd);
        else
          myCase = "";
        switch (myCase) {
          case "prefix":
            prefixes.put(l.substring(idEnd + 1, l.indexOf(":")).trim(),
                l.substring(l.indexOf("<") + 1, l.indexOf(">")).trim());
            break;
          case "rule":
            String headTerm = l.substring(idEnd + 1, l.indexOf(":-")).trim();
            String[] tailTerms = l.substring(l.indexOf(":-") + 2, l.indexOf(".")).trim().split("&");
            rules.put(headTerm, tailTerms);
            break;
          case "conjecture":
            conjectures.add(l.substring(idEnd + 1, l.indexOf(".")).trim().split("&"));
            break;

          default:
            break;
        }
      }
      myReader.close();
    } catch (FileNotFoundException e) {
      System.out.println("An error occurred.");
      e.printStackTrace();
    }

    for (Map.Entry<String, String[]> entry : rules.entrySet()) {
      System.out.println("@ RULE");
      String query = "";
      for (String tailTerm : entry.getValue())
        query += getQueryStringFromTerm(tailTerm, "MATCH");
      query += getQueryStringFromTerm(entry.getKey(), "MERGE");
      System.out.println(query);
      session.run(query);
    }

    Result result = null;
    for (String[] conjecture : conjectures) {
      System.out.println("@ CONJECTURE");
      String query = "";
      for (String term : conjecture)
        query += getQueryStringFromTerm(term, "MATCH");
      query += "RETURN *";
      System.out.println(query);
      result = session.run(query);
      while (result.hasNext()) {
        for (Pair<String, Value> nameValue : result.next().fields()) {
          String uri = nameValue.value().get("uri").asString();
          String localName = uri.substring(uri.indexOf('#')+1, uri.length());
          if (!nameValue.key().equals(localName))
            System.out.print(nameValue.key() + " = " + localName + " ");
        }
        System.out.println();
  
      }
      System.out.println();
    }

    session.close();
    driver.close();
  }

  public static String getQueryStringFromTerm(String term, String prefix) {
    String predicate = term.substring(0, term.indexOf("(")).trim();
    String subject = term.substring(term.indexOf("(") + 1, term.indexOf(",")).trim();
    String object = term.substring(term.indexOf(",") + 1, term.indexOf(")")).trim();
    String query = "";

    query += prefix + " ";
    query += literalToURI(subject, prefix);
    query += "-[:" + predicate + "]->";
    query += literalToURI(object, prefix);

    return query + '\n';
  }

  public static String literalToURI(String literal, String prefix) {
    if (literal.contains("\'")) {
      literal = literal.substring(1, literal.length() - 1);
      String localName = literal.substring(literal.indexOf(':') + 1, literal.length());
      literal = prefixes.get(literal.substring(0, literal.indexOf(':'))) + localName;
      if (prefix.equals("MATCH"))
        return "(" + localName + " {uri:\'" + literal + "\'})";
      else
        return "(" + localName + " {uri:\'" + literal + "\'})" + "\nMERGE " + "(" + localName + ")";
    } else
      return "(" + literal + ")";
  }
}
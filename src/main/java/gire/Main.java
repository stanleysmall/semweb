package gire;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
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

  public static void main(String[] args) {
    Boolean overwrite = false;
    String fileName = "example.txt";

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
      File file = new File(fileName);
      System.out.println("STARTED PROCESSING " + fileName + "\n");
      Scanner scanner = new Scanner(file);
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        switch (line.contains("@") ? line.substring(line.indexOf("@") + 1, line.indexOf(" ")) : "") {
          case "prefix":
            prefixes.put(line.substring(line.indexOf(" ") + 1, line.indexOf(":")).trim(),
                line.substring(line.indexOf("<") + 1, line.indexOf(">")).trim());
            break;
          case "rule":
            System.out.println(line);
            String headTerm = line.substring(line.indexOf(" ") + 1, line.indexOf(":-")).trim();
            String[] tailTerms = line.substring(line.indexOf(":-") + 2, line.indexOf(".")).trim().split("&");
            String ruleQuery = "";
            for (String tailTerm : tailTerms)
              ruleQuery += getQueryStringFromTerm(tailTerm, "MATCH");
            ruleQuery += getQueryStringFromTerm(headTerm, "MERGE");
            System.out.println(ruleQuery);
            session.run(ruleQuery);
            break;
          case "conjecture":
            System.out.println(line);
            String conectureQuery = "";
            for (String term : line.substring(line.indexOf(" ") + 1, line.indexOf(".")).trim().split("&"))
              conectureQuery += getQueryStringFromTerm(term, "MATCH");
            conectureQuery += "RETURN *";
            System.out.println(conectureQuery);
            Result result = session.run(conectureQuery);
            while (result.hasNext()) {
              for (Pair<String, Value> nameValue : result.next().fields()) {
                String uri = nameValue.value().get("uri").asString();
                String localName = uri.substring(uri.indexOf('#') + 1, uri.length());
                if (!nameValue.key().equals(localName))
                  System.out.print(nameValue.key() + " = " + localName + " ");
              }
              System.out.println();

            }
            System.out.println();
            break;

          default:
            break;
        }
      }
      scanner.close();
    } catch (FileNotFoundException e) {
      System.out.println("An error occurred.");
      e.printStackTrace();
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
      String id = "(" + localName + " {uri:\'" + literal + "\'})";
      return prefix.equals("MATCH") ? id : id + "\nMERGE " + "(" + localName + ")";
    } else
      return "(" + literal + ")";
  }
}
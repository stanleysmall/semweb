package gire;

import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
// import org.neo4j.driver.Result;
// import org.neo4j.driver.Transaction;
// import org.neo4j.driver.TransactionWork;

public class Main {
    public static void main(String[] args) {
        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "password"));
        Session session = driver.session();

        FileManager.get().addLocatorClassLoader(Main.class.getClassLoader());
        Model model = FileManager.get().loadModel("wine.rdf");

        StmtIterator iter = model.listStatements();
        
        while (iter.hasNext())
        {
            Statement stmt = iter.next();
            Property predicate = stmt.getPredicate();
            Resource subject = stmt.getSubject();
            RDFNode object = stmt.getObject();

            String subjectURI = subject.getURI();
            String objectURI = null;

            try {
                objectURI = object.asResource().getURI();
            } catch (Exception e) {}

            if (subjectURI == null || subjectURI.length() == 0 || subjectURI.equals("null"))
                    subjectURI = subject.toString();

            if (objectURI == null || objectURI.length() == 0 || objectURI.equals("null"))
                    objectURI = object.toString();

            if (predicate.getLocalName().equals("type")) {
                session.run("MERGE (subject {uri:\""+subjectURI+"\"}) SET subject :" + object.asResource().getLocalName());
            }
            else {
                session.run("MERGE (subject {uri:\""+subjectURI+"\"})");
                session.run("MERGE (object {uri:\""+objectURI+"\"})");
                session.run("MATCH (subject {uri:\""+subjectURI+"\"}), (object {uri:\""+objectURI+"\"}) " + 
                "MERGE((subject)-[:" + predicate.getLocalName() + " {uri: \"" + predicate.getURI() + "\"}]-> (object));");
            }
            
        }

        session.close();
        driver.close();
    }
}
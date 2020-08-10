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
            RDFNode obj = stmt.getObject();

            System.out.println("Subject = " + subject.getLocalName());
            session.run("CREATE (s) -[:" + predicate.getLocalName() + "]-> (t)");
            System.out.println("Object = " + obj.toString().replaceAll(".+#", ""));

        }

        session.close();
        driver.close();
    }
}
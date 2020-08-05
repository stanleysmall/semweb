package gire;

import org.apache.jena.Model;

public class Main {
    public static void main(String[] args) {
        Model model = ModelFactory.createDefaultModel() ; 
        model.read("wine.rdf") ;
        System.out.println("HEllO");
    }
}
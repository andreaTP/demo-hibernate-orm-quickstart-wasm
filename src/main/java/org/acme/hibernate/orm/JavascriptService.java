package org.acme.hibernate.orm;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JavascriptService {

    public void installFunction(String code) {

    }

    public int compute(String name) {
        // Javascript code goes here
        return name.length() * 2;
    }

}

package com.toefly.archbad.adapter.in.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderEndpoint {

    @Autowired
    private Object dependency;

    @Transactional
    public void place() {}
}

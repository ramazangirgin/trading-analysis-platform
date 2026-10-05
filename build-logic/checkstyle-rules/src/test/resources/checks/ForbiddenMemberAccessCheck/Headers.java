package com.example.app;

import static java.lang.System.out;

import java.util.List;

class Headers {
    void run(Holder holder) {
        holder.value.touch(); // violation
        lookup().value.touch(); // violation
        this.value.touch(); // violation
        Headers.class.getName();
        Headers.this.touch();
        holder.new Inner();
        touch();
    }
}

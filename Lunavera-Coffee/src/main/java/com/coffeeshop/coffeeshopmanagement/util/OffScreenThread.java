package com.coffeeshop.coffeeshopmanagement.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that reads or writes the database and must only be called from a background step, such as the first argument of
 * {@link Async#run} (TODO 6.14). Its body may then make database calls; {@code DatabaseOffTheScreenThreadTest} counts a call to it
 * from the screen thread exactly like a call to a DAO, so the mark cannot hide one.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface OffScreenThread {
}

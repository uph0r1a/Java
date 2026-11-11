package com.coffeeshop.coffeeshopmanagement.model;

/**
 * The two kinds of option a product can have (TODO 4.47): one SIZE out of a short list (S / M / L, each with a price difference and
 * how much of the recipe it uses), and any number of EXTRAs added on top (an extra shot, a topping), each with a price. The name is
 * what is stored in {@code product_options.kind}.
 */
public enum OptionKind {
    SIZE,
    EXTRA
}

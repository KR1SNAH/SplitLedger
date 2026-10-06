package com.splitledger.split;

/** The split can't be done as asked (e.g. percentages that don't add up to 100). The message is shown to the user. */
public class SplitException extends RuntimeException {

    public SplitException(String message) {
        super(message);
    }
}

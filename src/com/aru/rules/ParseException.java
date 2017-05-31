package com.aru.rules;


/**
 * When replacing {@link SubRule} placeholders (the '#' character) in rules, this exception may
 * be thrown if no suitable subrule can be found.
 */
public class ParseException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public ParseException(String msg) {
		super(msg);
	}

}

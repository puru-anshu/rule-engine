package com.aru.rules;


/**
 * A functional interface, so that we can write actions using java 8 lambdas.
 */
public interface ExecutableAction<Input, Output> {

	/**
	 * Called by the {@link com.aru.rules.JavascriptEngine} when the associated rule is the winning rule.
	 * @param input a bean containing all the attributes required by the expression contained in the associated rule.
	 * @return implementation specific
	 */
	Output execute(Input input);

}
